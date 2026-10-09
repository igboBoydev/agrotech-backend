package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.ReferralStatus;
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
@Table(name = "mfb_referrals")
public class MfbReferral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @ManyToOne
    @JoinColumn(name = "FARMER_ID")
    private FarmersDetails farmers;

    @ManyToOne
    @JoinColumn(name = "CREDIT_SCORE_ID")
    private CreditScoreRecord creditScore;

    @Column(name = "MFB_NAME")
    private String mfbName;

    @Column(name = "REFERRAL_FEE")
    private BigDecimal referralFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private ReferralStatus status;

    @Column(name = "REFERRED_AT")
    private String referredAt;
}
