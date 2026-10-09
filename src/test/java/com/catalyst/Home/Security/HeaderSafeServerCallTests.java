package com.catalyst.Home.Security;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Direct test of the actual defect this class works around: close() must always be
 * preceded by a sendHeaders() call on the real delegate, exactly once, regardless of
 * whether the caller ever sent headers itself. See the class-level javadoc on
 * HeaderSafeServerCall for why (grpc-servlet-jakarta doesn't emit a valid trailers-only
 * response otherwise — reproduced live with grpcurl against both JwtAuthServerInterceptor's
 * early auth rejections and every GrpcController's respond() helper's onError() calls).
 */
class HeaderSafeServerCallTests {

	@SuppressWarnings("unchecked")
	private ServerCall<Object, Object> mockDelegate() {
		return mock(ServerCall.class);
	}

	@Test
	void close_withoutPriorSendHeaders_sendsEmptyHeadersFirst() {
		ServerCall<Object, Object> delegate = mockDelegate();
		HeaderSafeServerCall<Object, Object> call = new HeaderSafeServerCall<>(delegate);
		Metadata trailers = new Metadata();

		call.close(Status.UNAUTHENTICATED, trailers);

		// Metadata has no value-based equals(), so verify with the same instance passed in
		// (trailers) rather than a fresh new Metadata() that would never match by reference.
		InOrder order = inOrder(delegate);
		order.verify(delegate).sendHeaders(any(Metadata.class));
		order.verify(delegate).close(Status.UNAUTHENTICATED, trailers);
	}

	@Test
	void close_afterHeadersAlreadySent_doesNotSendHeadersAgain() {
		ServerCall<Object, Object> delegate = mockDelegate();
		HeaderSafeServerCall<Object, Object> call = new HeaderSafeServerCall<>(delegate);

		Metadata realHeaders = new Metadata();
		call.sendHeaders(realHeaders);
		call.close(Status.OK, new Metadata());

		// Exactly once total (the real send, not a second one from close()) — guards against a
		// regression where close() blindly sends headers even when the success path already
		// did, which would be a second sendHeaders() call that gRPC transports reject.
		verify(delegate, times(1)).sendHeaders(any(Metadata.class));
		verify(delegate).sendHeaders(realHeaders);
	}

}
