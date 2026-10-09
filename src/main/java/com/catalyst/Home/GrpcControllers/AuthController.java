package com.catalyst.Home.GrpcControllers;

import auth.Auth;
import auth.AuthDataServiceGrpc;
import com.catalyst.Home.Services.AuthService;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class AuthController extends AuthDataServiceGrpc.AuthDataServiceImplBase {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void generateToken(Auth.GenerateTokenRequest request, StreamObserver<Auth.GenerateTokenResponse> responseObserver) {
        responseObserver.onNext(authService.generateToken(request));
        responseObserver.onCompleted();
    }

    @Override
    public void validateToken(Auth.TokenRequest request, StreamObserver<Auth.TokenResponse> responseObserver) {
        responseObserver.onNext(authService.validateToken(request));
        responseObserver.onCompleted();
    }

    @Override
    public void validateEmail(Auth.ValidateEmailRequest request, StreamObserver<Auth.ValidateEmailResponse> responseObserver) {
        responseObserver.onNext(authService.validateEmail(request));
        responseObserver.onCompleted();
    }

    @Override
    public void validatePhone(Auth.ValidatePhoneRequest request, StreamObserver<Auth.ValidatePhoneResponse> responseObserver) {
        responseObserver.onNext(authService.validatePhone(request));
        responseObserver.onCompleted();
    }

    @Override
    public void changeFarmerPassword(Auth.ChangeFarmerPasswordRequest request, StreamObserver<Auth.ChangeFarmerPasswordResponse> responseObserver) {
        responseObserver.onNext(authService.changeFarmerPassword(request));
        responseObserver.onCompleted();
    }

    @Override
    public void farmerLogin(Auth.FarmerLoginRequest request, StreamObserver<Auth.FarmerLoginResponse> responseObserver) {
        responseObserver.onNext(authService.farmerLogin(request));
        responseObserver.onCompleted();
    }

}
