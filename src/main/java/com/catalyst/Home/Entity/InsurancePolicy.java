package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.PolicyStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "insurance_policies")
public class InsurancePolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @ManyToOne
    @JoinColumn(name = "FARMER_ID")
    private FarmersDetails farmers;

    @Column(name = "POLICY_TYPE")
    private String policyType;

    @Column(name = "COVERAGE_AMOUNT")
    private BigDecimal coverageAmount;

    @Column(name = "PREMIUM_AMOUNT")
    private BigDecimal premiumAmount;

    @Column(name = "COMMISSION_AMOUNT")
    private BigDecimal commissionAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private PolicyStatus status;

    @Column(name = "START_DATE")
    private String startDate;

    @Column(name = "END_DATE")
    private String endDate;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InsuranceClaim> claims;
}
