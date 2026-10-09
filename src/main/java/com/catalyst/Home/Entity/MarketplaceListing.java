package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.ListingStatus;
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
@Table(name = "marketplace_listings")
public class MarketplaceListing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @ManyToOne
    @JoinColumn(name = "PRODUCT_ID")
    private FarmProducts farmProduct;

    @ManyToOne
    @JoinColumn(name = "FARMER_ID")
    private FarmersDetails sellerFarmer;

    @Column(name = "PRICE_PER_UNIT")
    private BigDecimal pricePerUnit;

    @Column(name = "QUANTITY_AVAILABLE")
    private Double quantityAvailable;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private ListingStatus status;

    @Column(name = "CREATED_AT")
    private String createdAt;
}
