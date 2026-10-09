package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.ClaimStatus;
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
@Table(name = "insurance_claims")
public class InsuranceClaim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @ManyToOne
    @JoinColumn(name = "POLICY_ID")
    private InsurancePolicy policy;

    @Column(name = "CLAIM_AMOUNT")
    private BigDecimal claimAmount;

    @Column(name = "PAYOUT_AMOUNT")
    private BigDecimal payoutAmount;

    @Column(name = "DESCRIPTION")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private ClaimStatus status;

    @Column(name = "FILED_AT")
    private String filedAt;

    @Column(name = "DECIDED_AT")
    private String decidedAt;
}
