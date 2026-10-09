package com.catalyst.Home.GrpcControllers;

import com.catalyst.Home.DTO.response.GrpcResponse;
import com.catalyst.Home.Services.MarketplaceService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import marketplace.Listing;
import marketplace.MarketplaceServiceGrpc;
import marketplace.Order;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class MarketplaceController extends MarketplaceServiceGrpc.MarketplaceServiceImplBase {

    private final MarketplaceService marketplaceService;

    public MarketplaceController(MarketplaceService marketplaceService) {
        this.marketplaceService = marketplaceService;
    }

    @Override
    public void createListing(Listing.CreateListingRequest request, StreamObserver<Listing.CreateListingResponse> responseObserver) {
        respond(marketplaceService.createListing(request), responseObserver);
    }

    @Override
    public void getListing(Listing.GetListingRequest request, StreamObserver<Listing.GetListingResponse> responseObserver) {
        respond(marketplaceService.getListing(request), responseObserver);
    }

    @Override
    public void getListingsBySeller(Listing.GetListingsBySellerRequest request, StreamObserver<Listing.GetListingsBySellerResponse> responseObserver) {
        respond(marketplaceService.getListingsBySeller(request), responseObserver);
    }

    @Override
    public void getActiveListings(Listing.GetActiveListingsRequest request, StreamObserver<Listing.GetActiveListingsResponse> responseObserver) {
        respond(marketplaceService.getActiveListings(request), responseObserver);
    }

    @Override
    public void placeOrder(Order.PlaceOrderRequest request, StreamObserver<Order.PlaceOrderResponse> responseObserver) {
        respond(marketplaceService.placeOrder(request), responseObserver);
    }

    @Override
    public void confirmOrder(Order.ConfirmOrderRequest request, StreamObserver<Order.ConfirmOrderResponse> responseObserver) {
        respond(marketplaceService.confirmOrder(request), responseObserver);
    }

    @Override
    public void completeOrder(Order.CompleteOrderRequest request, StreamObserver<Order.CompleteOrderResponse> responseObserver) {
        respond(marketplaceService.completeOrder(request), responseObserver);
    }

    @Override
    public void cancelOrder(Order.CancelOrderRequest request, StreamObserver<Order.CancelOrderResponse> responseObserver) {
        respond(marketplaceService.cancelOrder(request), responseObserver);
    }

    @Override
    public void getOrder(Order.GetOrderRequest request, StreamObserver<Order.GetOrderResponse> responseObserver) {
        respond(marketplaceService.getOrder(request), responseObserver);
    }

    @Override
    public void getOrdersByBuyer(Order.GetOrdersByBuyerRequest request, StreamObserver<Order.GetOrdersByBuyerResponse> responseObserver) {
        respond(marketplaceService.getOrdersByBuyer(request), responseObserver);
    }

    @Override
    public void getOrdersBySeller(Order.GetOrdersBySellerRequest request, StreamObserver<Order.GetOrdersBySellerResponse> responseObserver) {
        respond(marketplaceService.getOrdersBySeller(request), responseObserver);
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
