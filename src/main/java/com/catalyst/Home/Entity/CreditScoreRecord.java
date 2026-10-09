package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.CreditScoreBand;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "credit_score_records")
public class CreditScoreRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @ManyToOne
    @JoinColumn(name = "FARMER_ID")
    private FarmersDetails farmers;

    @Column(name = "SCORE")
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(name = "BAND")
    private CreditScoreBand band;

    @Column(name = "COMPUTED_AT")
    private String computedAt;
}
