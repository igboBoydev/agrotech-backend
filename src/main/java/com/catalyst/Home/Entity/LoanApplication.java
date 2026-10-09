package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.LoanApplicationStatus;
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
@Table(name = "loan_applications")
public class LoanApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @ManyToOne
    @JoinColumn(name = "FARMER_ID")
    private FarmersDetails farmers;

    @Column(name = "REQUESTED_AMOUNT")
    private BigDecimal requestedAmount;

    @Column(name = "TERM_MONTHS")
    private Integer termMonths;

    @Column(name = "PURPOSE")
    private String purpose;

    @Column(name = "INTEREST_RATE")
    private Double interestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private LoanApplicationStatus status;

    @Column(name = "APPLIED_AT")
    private String appliedAt;

    @Column(name = "DECIDED_AT")
    private String decidedAt;
}
