package com.catalyst.Home.Config;

import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
@GlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GrpcLoggingInterceptor implements ServerInterceptor {

    private static final int MAX_PAYLOAD_CHARS = 2000;

    // masks values of sensitive fields in the text-format output, e.g.  password: "abc" -> password: "***"
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)((?:password|token|secret|pin)\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        final String method = call.getMethodDescriptor().getFullMethodName();
        final long startNanos = System.nanoTime();

        log.info("gRPC call started: {}", method);

        ServerCall<ReqT, RespT> loggingCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void sendMessage(RespT message) {
                log.info("gRPC response: {} payload={}", method, format(message));
                super.sendMessage(message);
            }

            @Override
            public void close(Status status, Metadata trailers) {
                long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                if (status.isOk()) {
                    log.info("gRPC call finished: {} status=OK duration={}ms", method, ms);
                } else {
                    log.warn("gRPC call finished: {} status={} description={} duration={}ms",
                            method, status.getCode(), status.getDescription(), ms);
                }
                super.close(status, trailers);
            }
        };

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(
                next.startCall(loggingCall, headers)) {

            @Override
            public void onMessage(ReqT message) {
                log.info("gRPC request: {} payload={}", method, format(message));
                super.onMessage(message);
            }

            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose(); // the service method runs here for unary calls
                } catch (RuntimeException e) {
                    log.error("gRPC handler threw: {}", method, e);
                    throw e;
                }
            }
        };
    }

    private String format(Object message) {
        String text = message instanceof MessageOrBuilder m
                ? TextFormat.printer().emittingSingleLine(true).printToString(m)
                : String.valueOf(message);
        text = SENSITIVE.matcher(text).replaceAll("$1\"***\"");
        return text.length() > MAX_PAYLOAD_CHARS
                ? text.substring(0, MAX_PAYLOAD_CHARS) + "...(truncated)"
                : text;
    }
}
