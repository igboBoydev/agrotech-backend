package com.catalyst.Home.Security;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;

/**
 * Wraps a ServerCall so close() always has sendHeaders() called first if nothing
 * already has been sent.
 *
 * Needed because grpc-servlet-jakarta (the transport this app uses to serve gRPC
 * over Tomcat, confirmed on the classpath) does not correctly emit a trailers-only
 * HTTP/2 response when close() runs without sendHeaders() ever having been called —
 * reproduced live with grpcurl: a direct gRPC client got "Internal: server closed
 * the stream without sending trailers" instead of the real status. This hits every
 * StreamObserver.onError() call in this codebase (every GrpcController's respond()
 * helper uses it for ordinary business errors — 404/409/403/etc. — since
 * onError() is itself implemented via a bare ServerCall.close() with no prior
 * sendHeaders()), not just JwtAuthServerInterceptor's own early auth rejections.
 * A gRPC-Web client through Envoy tolerated/normalized the malformed response,
 * which is why this went unnoticed until a direct native gRPC client was tested.
 *
 * JwtAuthServerInterceptor wraps every call in this globally, once, at the top of
 * the interceptor chain — so this fixes the issue for all 8 gRPC services without
 * touching any of their controllers individually.
 */
final class HeaderSafeServerCall<ReqT, RespT> extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {

    private boolean headersSent = false;

    HeaderSafeServerCall(ServerCall<ReqT, RespT> delegate) {
        super(delegate);
    }

    @Override
    public void sendHeaders(Metadata headers) {
        headersSent = true;
        super.sendHeaders(headers);
    }

    @Override
    public void close(Status status, Metadata trailers) {
        if (!headersSent) {
            headersSent = true;
            super.sendHeaders(new Metadata());
        }
        super.close(status, trailers);
    }
}
