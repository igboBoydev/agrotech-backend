package com.catalyst.Home.Services.Impl;

import com.catalyst.Home.Dto.response.GrpcResponse;
import com.catalyst.Home.Entity.FarmProducts;
import com.catalyst.Home.Entity.FarmersDetails;
import com.catalyst.Home.Entity.MarketplaceListing;
import com.catalyst.Home.Entity.MarketplaceOrder;
import com.catalyst.Home.Repositories.FarmProductsRepository;
import com.catalyst.Home.Repositories.FarmerRepository;
import com.catalyst.Home.Repositories.MarketplaceListingRepository;
import com.catalyst.Home.Repositories.MarketplaceOrderRepository;
import com.catalyst.Home.Services.MarketplaceService;
import com.catalyst.Home.Utils.BuyerType;
import com.catalyst.Home.Utils.EscrowStatus;
import com.catalyst.Home.Utils.ListingStatus;
import com.catalyst.Home.Utils.OrderStatus;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import marketplace.Listing;
import marketplace.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketplaceServiceImpl implements MarketplaceService {
    private final FarmerRepository farmerRepository;
    private final FarmProductsRepository farmProductsRepository;
    private final MarketplaceListingRepository marketplaceListingRepository;
    private final MarketplaceOrderRepository marketplaceOrderRepository;

    private static final BigDecimal COMMISSION_RATE = BigDecimal.valueOf(0.035);

    @Override
    @Transactional
    public GrpcResponse<Listing.CreateListingResponse> createListing(Listing.CreateListingRequest request) {
        Optional<FarmersDetails> farmerOpt = farmerRepository.findByUuid(request.getFarmerId());
        if (farmerOpt.isEmpty()) {
            return GrpcResponse.<Listing.CreateListingResponse>builder()
                    .code("404").message("Farmer not found").status(Status.Code.NOT_FOUND).build();
        }

        Long productId;
        try {
            productId = Long.parseLong(request.getProductId());
        } catch (NumberFormatException e) {
            return GrpcResponse.<Listing.CreateListingResponse>builder()
                    .code("400").message("Invalid product id").status(Status.Code.INVALID_ARGUMENT).build();
        }

        Optional<FarmProducts> productOpt = farmProductsRepository.findById(productId);
        if (productOpt.isEmpty()) {
            return GrpcResponse.<Listing.CreateListingResponse>builder()
                    .code("404").message("Product not found").status(Status.Code.NOT_FOUND).build();
        }
        FarmersDetails farmer = farmerOpt.get();
        FarmProducts product = productOpt.get();

        if (product.getFarm() == null || product.getFarm().getFarmers() == null
                || !farmer.getUuid().equals(product.getFarm().getFarmers().getUuid())) {
            return GrpcResponse.<Listing.CreateListingResponse>builder()
                    .code("403").message("Product does not belong to this farmer").status(Status.Code.PERMISSION_DENIED).build();
        }

        MarketplaceListing listing = marketplaceListingRepository.save(MarketplaceListing.builder()
                .uuid(UUID.randomUUID().toString())
                .farmProduct(product)
                .sellerFarmer(farmer)
                .pricePerUnit(BigDecimal.valueOf(request.getPricePerUnit()))
                .quantityAvailable(request.getQuantityAvailable())
                .status(ListingStatus.LISTING_ACTIVE)
                .createdAt(LocalDate.now().toString())
                .build());

        Listing.CreateListingResponse response = Listing.CreateListingResponse.newBuilder()
                .setCode("200")
                .setMessage("Listing created successfully")
                .setListing(toListingProto(listing))
                .build();

        return GrpcResponse.<Listing.CreateListingResponse>builder()
                .code("200").message("Listing created successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Listing.GetListingResponse> getListing(Listing.GetListingRequest request) {
        Optional<MarketplaceListing> listingOpt = marketplaceListingRepository.findByUuid(request.getListingId());
        if (listingOpt.isEmpty()) {
            return GrpcResponse.<Listing.GetListingResponse>builder()
                    .code("404").message("Listing not found").status(Status.Code.NOT_FOUND).build();
        }

        Listing.GetListingResponse response = Listing.GetListingResponse.newBuilder()
                .setCode("200")
                .setMessage("Listing retrieved successfully")
                .setListing(toListingProto(listingOpt.get()))
                .build();

        return GrpcResponse.<Listing.GetListingResponse>builder()
                .code("200").message("Listing retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Listing.GetListingsBySellerResponse> getListingsBySeller(Listing.GetListingsBySellerRequest request) {
        List<MarketplaceListing> listings = marketplaceListingRepository.findBySellerFarmer_Uuid(request.getFarmerId());

        Listing.GetListingsBySellerResponse response = Listing.GetListingsBySellerResponse.newBuilder()
                .addAllListings(listings.stream().map(this::toListingProto).toList())
                .build();

        return GrpcResponse.<Listing.GetListingsBySellerResponse>builder()
                .code("200").message("Listings retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Listing.GetActiveListingsResponse> getActiveListings(Listing.GetActiveListingsRequest request) {
        List<MarketplaceListing> listings = marketplaceListingRepository.findByStatus(ListingStatus.LISTING_ACTIVE);

        Listing.GetActiveListingsResponse response = Listing.GetActiveListingsResponse.newBuilder()
                .addAllListings(listings.stream().map(this::toListingProto).toList())
                .build();

        return GrpcResponse.<Listing.GetActiveListingsResponse>builder()
                .code("200").message("Active listings retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Order.PlaceOrderResponse> placeOrder(Order.PlaceOrderRequest request) {
        Optional<MarketplaceListing> listingOpt = marketplaceListingRepository.findByUuid(request.getListingId());
        if (listingOpt.isEmpty()) {
            return GrpcResponse.<Order.PlaceOrderResponse>builder()
                    .code("404").message("Listing not found").status(Status.Code.NOT_FOUND).build();
        }
        MarketplaceListing listing = listingOpt.get();
        if (listing.getStatus() != ListingStatus.LISTING_ACTIVE) {
            return GrpcResponse.<Order.PlaceOrderResponse>builder()
                    .code("409").message("Listing is not active").status(Status.Code.FAILED_PRECONDITION).build();
        }
        if (request.getQuantity() <= 0 || request.getQuantity() > listing.getQuantityAvailable()) {
            return GrpcResponse.<Order.PlaceOrderResponse>builder()
                    .code("409").message("Insufficient quantity available").status(Status.Code.FAILED_PRECONDITION).build();
        }

        BigDecimal unitPrice = listing.getPricePerUnit();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));
        BigDecimal commissionAmount = totalAmount.multiply(COMMISSION_RATE);

        double remaining = listing.getQuantityAvailable() - request.getQuantity();
        listing.setQuantityAvailable(remaining);
        if (remaining <= 0) {
            listing.setQuantityAvailable(0.0);
            listing.setStatus(ListingStatus.LISTING_SOLD_OUT);
        }
        marketplaceListingRepository.save(listing);

        String now = LocalDate.now().toString();
        MarketplaceOrder order = marketplaceOrderRepository.save(MarketplaceOrder.builder()
                .uuid(UUID.randomUUID().toString())
                .listing(listing)
                .buyerId(request.getBuyerId())
                .buyerType(mapBuyerType(request.getBuyerType()))
                .quantity(request.getQuantity())
                .unitPrice(unitPrice)
                .totalAmount(totalAmount)
                .commissionAmount(commissionAmount)
                .status(OrderStatus.ORDER_PENDING)
                .escrowStatus(EscrowStatus.ESCROW_HELD)
                .createdAt(now)
                .updatedAt(now)
                .build());

        Order.PlaceOrderResponse response = Order.PlaceOrderResponse.newBuilder()
                .setCode("200")
                .setMessage("Order placed successfully")
                .setOrder(toOrderProto(order))
                .build();

        return GrpcResponse.<Order.PlaceOrderResponse>builder()
                .code("200").message("Order placed successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Order.ConfirmOrderResponse> confirmOrder(Order.ConfirmOrderRequest request) {
        Optional<MarketplaceOrder> orderOpt = marketplaceOrderRepository.findByUuid(request.getOrderId());
        if (orderOpt.isEmpty()) {
            return GrpcResponse.<Order.ConfirmOrderResponse>builder()
                    .code("404").message("Order not found").status(Status.Code.NOT_FOUND).build();
        }
        MarketplaceOrder order = orderOpt.get();
        if (order.getStatus() != OrderStatus.ORDER_PENDING) {
            return GrpcResponse.<Order.ConfirmOrderResponse>builder()
                    .code("409").message("Order has already been confirmed").status(Status.Code.FAILED_PRECONDITION).build();
        }

        order.setUpdatedAt(LocalDate.now().toString());
        if (request.getAccept()) {
            order.setStatus(OrderStatus.ORDER_ACCEPTED);
        } else {
            order.setStatus(OrderStatus.ORDER_REJECTED);
            order.setEscrowStatus(EscrowStatus.ESCROW_REFUNDED);
            restoreListingQuantity(order);
        }
        marketplaceOrderRepository.save(order);

        Order.ConfirmOrderResponse response = Order.ConfirmOrderResponse.newBuilder()
                .setCode("200")
                .setMessage(request.getAccept() ? "Order accepted" : "Order rejected")
                .setOrder(toOrderProto(order))
                .build();

        return GrpcResponse.<Order.ConfirmOrderResponse>builder()
                .code("200").message(response.getMessage()).status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Order.CompleteOrderResponse> completeOrder(Order.CompleteOrderRequest request) {
        Optional<MarketplaceOrder> orderOpt = marketplaceOrderRepository.findByUuid(request.getOrderId());
        if (orderOpt.isEmpty()) {
            return GrpcResponse.<Order.CompleteOrderResponse>builder()
                    .code("404").message("Order not found").status(Status.Code.NOT_FOUND).build();
        }
        MarketplaceOrder order = orderOpt.get();
        if (order.getStatus() != OrderStatus.ORDER_ACCEPTED) {
            return GrpcResponse.<Order.CompleteOrderResponse>builder()
                    .code("409").message("Order must be accepted before it can be completed").status(Status.Code.FAILED_PRECONDITION).build();
        }

        order.setStatus(OrderStatus.ORDER_COMPLETED);
        order.setEscrowStatus(EscrowStatus.ESCROW_RELEASED);
        order.setUpdatedAt(LocalDate.now().toString());
        marketplaceOrderRepository.save(order);

        Order.CompleteOrderResponse response = Order.CompleteOrderResponse.newBuilder()
                .setCode("200")
                .setMessage("Order completed successfully")
                .setOrder(toOrderProto(order))
                .build();

        return GrpcResponse.<Order.CompleteOrderResponse>builder()
                .code("200").message("Order completed successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional
    public GrpcResponse<Order.CancelOrderResponse> cancelOrder(Order.CancelOrderRequest request) {
        Optional<MarketplaceOrder> orderOpt = marketplaceOrderRepository.findByUuid(request.getOrderId());
        if (orderOpt.isEmpty()) {
            return GrpcResponse.<Order.CancelOrderResponse>builder()
                    .code("404").message("Order not found").status(Status.Code.NOT_FOUND).build();
        }
        MarketplaceOrder order = orderOpt.get();
        if (order.getStatus() != OrderStatus.ORDER_PENDING && order.getStatus() != OrderStatus.ORDER_ACCEPTED) {
            return GrpcResponse.<Order.CancelOrderResponse>builder()
                    .code("409").message("Order cannot be cancelled in its current state").status(Status.Code.FAILED_PRECONDITION).build();
        }

        order.setStatus(OrderStatus.ORDER_CANCELLED);
        order.setEscrowStatus(EscrowStatus.ESCROW_REFUNDED);
        order.setUpdatedAt(LocalDate.now().toString());
        restoreListingQuantity(order);
        marketplaceOrderRepository.save(order);

        Order.CancelOrderResponse response = Order.CancelOrderResponse.newBuilder()
                .setCode("200")
                .setMessage("Order cancelled successfully")
                .setOrder(toOrderProto(order))
                .build();

        return GrpcResponse.<Order.CancelOrderResponse>builder()
                .code("200").message("Order cancelled successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Order.GetOrderResponse> getOrder(Order.GetOrderRequest request) {
        Optional<MarketplaceOrder> orderOpt = marketplaceOrderRepository.findByUuid(request.getOrderId());
        if (orderOpt.isEmpty()) {
            return GrpcResponse.<Order.GetOrderResponse>builder()
                    .code("404").message("Order not found").status(Status.Code.NOT_FOUND).build();
        }

        Order.GetOrderResponse response = Order.GetOrderResponse.newBuilder()
                .setCode("200")
                .setMessage("Order retrieved successfully")
                .setOrder(toOrderProto(orderOpt.get()))
                .build();

        return GrpcResponse.<Order.GetOrderResponse>builder()
                .code("200").message("Order retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Order.GetOrdersByBuyerResponse> getOrdersByBuyer(Order.GetOrdersByBuyerRequest request) {
        List<MarketplaceOrder> orders = marketplaceOrderRepository.findByBuyerId(request.getBuyerId());

        Order.GetOrdersByBuyerResponse response = Order.GetOrdersByBuyerResponse.newBuilder()
                .addAllOrders(orders.stream().map(this::toOrderProto).toList())
                .build();

        return GrpcResponse.<Order.GetOrdersByBuyerResponse>builder()
                .code("200").message("Orders retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    @Override
    @Transactional(readOnly = true)
    public GrpcResponse<Order.GetOrdersBySellerResponse> getOrdersBySeller(Order.GetOrdersBySellerRequest request) {
        List<MarketplaceOrder> orders = marketplaceOrderRepository.findByListing_SellerFarmer_Uuid(request.getFarmerId());

        Order.GetOrdersBySellerResponse response = Order.GetOrdersBySellerResponse.newBuilder()
                .addAllOrders(orders.stream().map(this::toOrderProto).toList())
                .build();

        return GrpcResponse.<Order.GetOrdersBySellerResponse>builder()
                .code("200").message("Orders retrieved successfully").status(Status.Code.OK).data(response).build();
    }

    private void restoreListingQuantity(MarketplaceOrder order) {
        MarketplaceListing listing = order.getListing();
        if (listing == null) {
            return;
        }
        double restored = (listing.getQuantityAvailable() == null ? 0 : listing.getQuantityAvailable()) + order.getQuantity();
        listing.setQuantityAvailable(restored);
        if (listing.getStatus() == ListingStatus.LISTING_SOLD_OUT && restored > 0) {
            listing.setStatus(ListingStatus.LISTING_ACTIVE);
        }
        marketplaceListingRepository.save(listing);
    }

    private Listing.MarketplaceListing toListingProto(MarketplaceListing listing) {
        return Listing.MarketplaceListing.newBuilder()
                .setId(nullToEmpty(listing.getUuid()))
                .setProductId(listing.getFarmProduct() != null ? String.valueOf(listing.getFarmProduct().getId()) : "")
                .setFarmerId(listing.getSellerFarmer() != null ? nullToEmpty(listing.getSellerFarmer().getUuid()) : "")
                .setPricePerUnit(listing.getPricePerUnit() == null ? 0 : listing.getPricePerUnit().doubleValue())
                .setQuantityAvailable(listing.getQuantityAvailable() == null ? 0 : listing.getQuantityAvailable())
                .setStatus(mapListingStatus(listing.getStatus()))
                .setCreatedAt(nullToEmpty(listing.getCreatedAt()))
                .build();
    }

    private Order.MarketplaceOrder toOrderProto(MarketplaceOrder order) {
        return Order.MarketplaceOrder.newBuilder()
                .setId(nullToEmpty(order.getUuid()))
                .setListingId(order.getListing() != null ? nullToEmpty(order.getListing().getUuid()) : "")
                .setBuyerId(nullToEmpty(order.getBuyerId()))
                .setBuyerType(mapBuyerType(order.getBuyerType()))
                .setQuantity(order.getQuantity() == null ? 0 : order.getQuantity())
                .setUnitPrice(order.getUnitPrice() == null ? 0 : order.getUnitPrice().doubleValue())
                .setTotalAmount(order.getTotalAmount() == null ? 0 : order.getTotalAmount().doubleValue())
                .setCommissionAmount(order.getCommissionAmount() == null ? 0 : order.getCommissionAmount().doubleValue())
                .setStatus(mapOrderStatus(order.getStatus()))
                .setEscrowStatus(mapEscrowStatus(order.getEscrowStatus()))
                .setCreatedAt(nullToEmpty(order.getCreatedAt()))
                .setUpdatedAt(nullToEmpty(order.getUpdatedAt()))
                .build();
    }

    private static marketplace.Enum.ListingStatus mapListingStatus(ListingStatus status) {
        return marketplace.Enum.ListingStatus.valueOf(status.name());
    }

    private static marketplace.Enum.OrderStatus mapOrderStatus(OrderStatus status) {
        return marketplace.Enum.OrderStatus.valueOf(status.name());
    }

    private static marketplace.Enum.EscrowStatus mapEscrowStatus(EscrowStatus status) {
        return marketplace.Enum.EscrowStatus.valueOf(status.name());
    }

    private static marketplace.Enum.BuyerType mapBuyerType(BuyerType type) {
        return marketplace.Enum.BuyerType.valueOf(type.name());
    }

    private static BuyerType mapBuyerType(marketplace.Enum.BuyerType type) {
        return BuyerType.valueOf(type.name());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
