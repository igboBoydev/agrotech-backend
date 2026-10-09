package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.SubscriptionStatus;
import com.catalyst.Home.Utils.SubscriptionTier;
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
@Table(name = "subscriptions")
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @Column(name = "BUSINESS_ID")
    private String businessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIER")
    private SubscriptionTier tier;

    @Column(name = "PRICE")
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private SubscriptionStatus status;

    @Column(name = "START_DATE")
    private String startDate;

    @Column(name = "END_DATE")
    private String endDate;
}
