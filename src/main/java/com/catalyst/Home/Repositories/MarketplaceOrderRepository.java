package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.MarketplaceOrder;
import com.catalyst.Home.Utils.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, Long> {
    Optional<MarketplaceOrder> findByUuid(String uuid);
    List<MarketplaceOrder> findByBuyerId(String buyerId);
    List<MarketplaceOrder> findByListing_SellerFarmer_Uuid(String farmerUuid);
    List<MarketplaceOrder> findByStatus(OrderStatus status);
}
