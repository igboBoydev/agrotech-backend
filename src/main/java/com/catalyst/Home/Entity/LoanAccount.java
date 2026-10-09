package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.LoanAccountStatus;
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
@Table(name = "loan_accounts")
public class LoanAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @OneToOne
    @JoinColumn(name = "APPLICATION_ID", unique = true)
    private LoanApplication application;

    @ManyToOne
    @JoinColumn(name = "FARMER_ID")
    private FarmersDetails farmers;

    @Column(name = "PRINCIPAL_AMOUNT")
    private BigDecimal principalAmount;

    @Column(name = "INTEREST_RATE")
    private Double interestRate;

    @Column(name = "OUTSTANDING_BALANCE")
    private BigDecimal outstandingBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private LoanAccountStatus status;

    @Column(name = "DISBURSED_AT")
    private String disbursedAt;

    @OneToMany(mappedBy = "loanAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LoanRepayment> repayments;
}
