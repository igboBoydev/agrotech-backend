package com.catalyst.Home.Entity;

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
@Table(name = "loan_repayments")
public class LoanRepayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @ManyToOne
    @JoinColumn(name = "LOAN_ACCOUNT_ID")
    private LoanAccount loanAccount;

    @Column(name = "AMOUNT")
    private BigDecimal amount;

    @Column(name = "PAID_AT")
    private String paidAt;
}
