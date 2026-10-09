package com.catalyst.Home.GrpcControllers;

import analytics.AnalyticsServiceGrpc;
import analytics.Insights;
import analytics.SubscriptionOuterClass;
import com.catalyst.Home.DTO.response.GrpcResponse;
import com.catalyst.Home.Services.AnalyticsService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class AnalyticsController extends AnalyticsServiceGrpc.AnalyticsServiceImplBase {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public void createSubscription(SubscriptionOuterClass.CreateSubscriptionRequest request, StreamObserver<SubscriptionOuterClass.CreateSubscriptionResponse> responseObserver) {
        respond(analyticsService.createSubscription(request), responseObserver);
    }

    @Override
    public void getSubscription(SubscriptionOuterClass.GetSubscriptionRequest request, StreamObserver<SubscriptionOuterClass.GetSubscriptionResponse> responseObserver) {
        respond(analyticsService.getSubscription(request), responseObserver);
    }

    @Override
    public void cancelSubscription(SubscriptionOuterClass.CancelSubscriptionRequest request, StreamObserver<SubscriptionOuterClass.CancelSubscriptionResponse> responseObserver) {
        respond(analyticsService.cancelSubscription(request), responseObserver);
    }

    @Override
    public void getRegionalCropSummary(Insights.GetRegionalCropSummaryRequest request, StreamObserver<Insights.GetRegionalCropSummaryResponse> responseObserver) {
        respond(analyticsService.getRegionalCropSummary(request), responseObserver);
    }

    @Override
    public void getMarketplaceActivitySummary(Insights.GetMarketplaceActivitySummaryRequest request, StreamObserver<Insights.GetMarketplaceActivitySummaryResponse> responseObserver) {
        respond(analyticsService.getMarketplaceActivitySummary(request), responseObserver);
    }

    @Override
    public void getFoodSecurityIndicators(Insights.GetFoodSecurityIndicatorsRequest request, StreamObserver<Insights.GetFoodSecurityIndicatorsResponse> responseObserver) {
        respond(analyticsService.getFoodSecurityIndicators(request), responseObserver);
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
