package com.catalyst.Home.Services;

import com.catalyst.Home.Dto.response.GrpcResponse;
import marketplace.Listing;
import marketplace.Order;

public interface MarketplaceService {

    GrpcResponse<Listing.CreateListingResponse> createListing(Listing.CreateListingRequest request);

    GrpcResponse<Listing.GetListingResponse> getListing(Listing.GetListingRequest request);

    GrpcResponse<Listing.GetListingsBySellerResponse> getListingsBySeller(Listing.GetListingsBySellerRequest request);

    GrpcResponse<Listing.GetActiveListingsResponse> getActiveListings(Listing.GetActiveListingsRequest request);

    GrpcResponse<Order.PlaceOrderResponse> placeOrder(Order.PlaceOrderRequest request);

    GrpcResponse<Order.ConfirmOrderResponse> confirmOrder(Order.ConfirmOrderRequest request);

    GrpcResponse<Order.CompleteOrderResponse> completeOrder(Order.CompleteOrderRequest request);

    GrpcResponse<Order.CancelOrderResponse> cancelOrder(Order.CancelOrderRequest request);

    GrpcResponse<Order.GetOrderResponse> getOrder(Order.GetOrderRequest request);

    GrpcResponse<Order.GetOrdersByBuyerResponse> getOrdersByBuyer(Order.GetOrdersByBuyerRequest request);

    GrpcResponse<Order.GetOrdersBySellerResponse> getOrdersBySeller(Order.GetOrdersBySellerRequest request);
}
