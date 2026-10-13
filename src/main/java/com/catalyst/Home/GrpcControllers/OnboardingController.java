package com.catalyst.Home.GrpcControllers;

import com.catalyst.Home.Dto.response.GrpcResponse;
import com.catalyst.Home.Services.OnboardingService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;
import users.Business;
import users.Farmers;
import users.KycOuterClass;
import users.ProductsOuterClass;
import users.UsersServiceGrpc;

@GrpcService
public class OnboardingController extends UsersServiceGrpc.UsersServiceImplBase {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @Override
    public void createFarmer(Farmers.CreateFarmerRequest request, StreamObserver<Farmers.CreateFarmerResponse> responseObserver) {
        respond(onboardingService.createFarmer(request), responseObserver);
    }

    @Override
    public void addFarmerKyc(Farmers.AddFarmerKycRequest request, StreamObserver<Farmers.AddFarmerKycResponse> responseObserver) {
        respond(onboardingService.addFarmerKyc(request), responseObserver);
    }

    @Override
    public void getFarmerProfile(Farmers.FarmerProfileRequest request, StreamObserver<Farmers.FarmerProfileResponse> responseObserver) {
        respond(onboardingService.getFarmersProfile(request), responseObserver);
    }

    @Override
    public void addFarm(Farmers.AddFarmRequest request, StreamObserver<Farmers.AddFarmResponse> responseObserver) {
        respond(onboardingService.addFarm(request), responseObserver);
    }

    @Override
    public void getFarms(Farmers.GetFarmsRequest request, StreamObserver<Farmers.GetFarmsResponse> responseObserver) {
        respond(onboardingService.getFarms(request), responseObserver);
    }

    @Override
    public void checkKYCStatus(KycOuterClass.KYCRequest request, StreamObserver<KycOuterClass.KYCStatus> responseObserver) {
        respond(onboardingService.checkKycStatus(request), responseObserver);
    }

    @Override
    public void createBusiness(Business.CreateBusinessRequest request, StreamObserver<Business.CreateBusinessResponse> responseObserver) {
        respond(onboardingService.createBusiness(request), responseObserver);
    }

    @Override
    public void getBusinessProfile(Business.GetBusinessProfileRequest request, StreamObserver<Business.GetBusinessProfileResponse> responseObserver) {
        respond(onboardingService.getBusinessProfile(request), responseObserver);
    }

    @Override
    public void getFarmProducts(ProductsOuterClass.GetProductsRequest request, StreamObserver<ProductsOuterClass.GetProductsResponse> responseObserver) {
        respond(onboardingService.getFarmProducts(request), responseObserver);
    }

    @Override
    public void addFarmProduct(ProductsOuterClass.AddFarmProductRequest request, StreamObserver<ProductsOuterClass.AddFarmProductResponse> responseObserver) {
        respond(onboardingService.addFarmProduct(request), responseObserver);
    }

    private <T> void respond(GrpcResponse<T> grpcResponse, StreamObserver<T> responseObserver) {
        if (!grpcResponse.getCode().equals("200")) {
            Status status = Status.fromCode(grpcResponse.getStatus()).withDescription(grpcResponse.getMessage());
            StatusRuntimeException statusException = status.asRuntimeException();
            responseObserver.onError(statusException);
            return;
        }
        responseObserver.onNext(grpcResponse.getData());
        responseObserver.onCompleted();
    }
}
