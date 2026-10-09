package com.catalyst.Home.Security;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Enforces RPC-level authentication. Public (pre-login) RPCs are allowlisted below;
 * everything else requires a valid "authorization: Bearer <jwt>" metadata entry.
 * This is authentication only — it does not check that the caller's identity actually
 * owns the resource an RPC is acting on (per-resource authorization), which would mean
 * touching every RPC handler individually and is deliberately out of scope here.
 *
 * Also wraps every call in HeaderSafeServerCall (see that class for why) — this is the
 * one global interceptor covering all 8 gRPC services, so it's the natural place to
 * apply that fix everywhere without touching every controller individually.
 */
@Component
@GlobalServerInterceptor
@RequiredArgsConstructor
public class JwtAuthServerInterceptor implements ServerInterceptor {

    public static final Context.Key<Claims> CLAIMS_CONTEXT_KEY = Context.key("jwtClaims");

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final Set<String> PUBLIC_METHODS = Set.of(
            "auth.AuthDataService/FarmerLogin",
            "auth.AuthDataService/GenerateToken",
            "auth.AuthDataService/ValidateToken",
            "auth.AuthDataService/ValidateEmail",
            "auth.AuthDataService/ValidatePhone",
            "users.UsersService/CreateFarmer",
            "users.UsersService/CreateBusiness"
    );

    private final JwtService jwtService;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        ServerCall<ReqT, RespT> safeCall = new HeaderSafeServerCall<>(call);

        String fullMethodName = safeCall.getMethodDescriptor().getFullMethodName();
        if (isPublic(fullMethodName)) {
            return next.startCall(safeCall, headers);
        }

        String token = extractBearerToken(headers.get(AUTHORIZATION_KEY));
        if (token == null) {
            safeCall.close(Status.UNAUTHENTICATED.withDescription("Missing bearer token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        Optional<Claims> claimsOpt = jwtService.parseClaims(token);
        if (claimsOpt.isEmpty()) {
            safeCall.close(Status.UNAUTHENTICATED.withDescription("Invalid or expired token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        Context context = Context.current().withValue(CLAIMS_CONTEXT_KEY, claimsOpt.get());
        return Contexts.interceptCall(context, safeCall, headers, next);
    }

    private static boolean isPublic(String fullMethodName) {
        return PUBLIC_METHODS.contains(fullMethodName)
                || fullMethodName.startsWith("grpc.reflection.")
                || fullMethodName.startsWith("grpc.health.");
    }

    private static String extractBearerToken(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
