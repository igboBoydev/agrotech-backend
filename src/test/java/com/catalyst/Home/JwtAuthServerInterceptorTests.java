package com.catalyst.Home;

import com.catalyst.Home.Security.JwtAuthServerInterceptor;
import com.catalyst.Home.Security.JwtService;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthServerInterceptorTests {

	private static final Metadata.Key<String> AUTHORIZATION_KEY =
			Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

	private final JwtService jwtService = new JwtService(
			Base64.getEncoder().encodeToString("a-32-byte-test-secret-key-value!".getBytes()), 3_600_000L);

	private final JwtAuthServerInterceptor interceptor = new JwtAuthServerInterceptor(jwtService);

	@SuppressWarnings("unchecked")
	private ServerCall<Object, Object> mockCall(String fullMethodName) {
		ServerCall<Object, Object> call = mock(ServerCall.class);
		MethodDescriptor<Object, Object> methodDescriptor = mock(MethodDescriptor.class);
		when(methodDescriptor.getFullMethodName()).thenReturn(fullMethodName);
		when(call.getMethodDescriptor()).thenReturn(methodDescriptor);
		return call;
	}

	@SuppressWarnings("unchecked")
	private ServerCallHandler<Object, Object> mockHandler() {
		ServerCallHandler<Object, Object> handler = mock(ServerCallHandler.class);
		ServerCall.Listener<Object> listener = mock(ServerCall.Listener.class);
		when(handler.startCall(any(), any())).thenReturn(listener);
		return handler;
	}

	@Test
	void protectedMethod_missingToken_rejectedWithoutReachingHandler() {
		ServerCall<Object, Object> call = mockCall("fintech.FintechService/ApplyForLoan");
		ServerCallHandler<Object, Object> handler = mockHandler();
		Metadata headers = new Metadata();

		interceptor.interceptCall(call, headers, handler);

		verify(call).close(argThatStatusCode(Status.Code.UNAUTHENTICATED), any(Metadata.class));
		verify(handler, never()).startCall(any(), any());
	}

	@Test
	void protectedMethod_garbageToken_rejectedWithoutReachingHandler() {
		ServerCall<Object, Object> call = mockCall("fintech.FintechService/ApplyForLoan");
		ServerCallHandler<Object, Object> handler = mockHandler();
		Metadata headers = new Metadata();
		headers.put(AUTHORIZATION_KEY, "Bearer not-a-real-jwt");

		interceptor.interceptCall(call, headers, handler);

		verify(call).close(argThatStatusCode(Status.Code.UNAUTHENTICATED), any(Metadata.class));
		verify(handler, never()).startCall(any(), any());
	}

	@Test
	void protectedMethod_validToken_reachesHandler() {
		ServerCall<Object, Object> call = mockCall("fintech.FintechService/ApplyForLoan");
		ServerCallHandler<Object, Object> handler = mockHandler();
		String token = jwtService.generateToken("farmer@example.com", Map.of("role", "FARMER"));
		Metadata headers = new Metadata();
		headers.put(AUTHORIZATION_KEY, "Bearer " + token);

		ServerCall.Listener<Object> listener = interceptor.interceptCall(call, headers, handler);

		assertThat(listener).isNotNull();
		// Not eq(call): the interceptor now wraps every call in HeaderSafeServerCall (see that
		// class) before handing it to the handler, so the object identity is intentionally
		// different from the raw mock — HeaderSafeServerCallTests covers that wrapper directly.
		verify(handler).startCall(any(), eq(headers));
	}

	@Test
	void publicMethod_noToken_stillReachesHandler() {
		ServerCall<Object, Object> call = mockCall("auth.AuthDataService/FarmerLogin");
		ServerCallHandler<Object, Object> handler = mockHandler();
		Metadata headers = new Metadata();

		interceptor.interceptCall(call, headers, handler);

		verify(handler).startCall(any(), eq(headers));
	}

	private static Status argThatStatusCode(Status.Code code) {
		return org.mockito.ArgumentMatchers.argThat(status -> status != null && status.getCode() == code);
	}

}
