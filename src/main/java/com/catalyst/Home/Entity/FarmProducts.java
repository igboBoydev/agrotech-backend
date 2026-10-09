package com.catalyst.Home.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "farm_products")
public class FarmProducts {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "PRODUCT_NAME", nullable = false)
    private String productName;

    @Column(name = "CATEGORY")
    private String category;

    @Column(name = "PRODUCT_TYPE")
    private String productType;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "QUANTITY")
    private Double quantity;

    @Column(name = "UNIT")
    private String unit;

    @Column(name = "PRICE_PER_UNIT")
    private BigDecimal pricePerUnit;

    @Column(name = "TOTAL_VALUE")
    private BigDecimal totalValue;

    @Column(name = "HARVEST_DATE")
    private LocalDate harvestDate;

    @Column(name = "EXPIRY_DATE")
    private LocalDate expiryDate;

    @Column(name = "IS_AVAILABLE")
    private Boolean isAvailable;

    @ManyToOne
    @JoinColumn(name = "FARM_ID", nullable = false)
    private Farms farm;
}
