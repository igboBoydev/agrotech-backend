package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.BuyerType;
import com.catalyst.Home.Utils.EscrowStatus;
import com.catalyst.Home.Utils.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "marketplace_orders")
public class MarketplaceOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @ManyToOne
    @JoinColumn(name = "LISTING_ID")
    private MarketplaceListing listing;

    @Column(name = "BUYER_ID")
    private String buyerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "BUYER_TYPE")
    private BuyerType buyerType;

    @Column(name = "QUANTITY")
    private Double quantity;

    @Column(name = "UNIT_PRICE")
    private BigDecimal unitPrice;

    @Column(name = "TOTAL_AMOUNT")
    private BigDecimal totalAmount;

    @Column(name = "COMMISSION_AMOUNT")
    private BigDecimal commissionAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESCROW_STATUS")
    private EscrowStatus escrowStatus;

    @Column(name = "CREATED_AT")
    private String createdAt;

    @Column(name = "UPDATED_AT")
    private String updatedAt;
}
