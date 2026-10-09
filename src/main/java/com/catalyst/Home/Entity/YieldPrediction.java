package com.catalyst.Home.Entity;

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
@Table(name = "yield_predictions")
public class YieldPrediction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @ManyToOne
    @JoinColumn(name = "FARM_ID")
    private Farms farm;

    @Column(name = "CROP_TYPE")
    private String cropType;

    @Column(name = "PREDICTED_YIELD_TONS")
    private Double predictedYieldTons;

    @Column(name = "HEALTH_MULTIPLIER_USED")
    private Double healthMultiplierUsed;

    @Column(name = "COMPUTED_AT")
    private String computedAt;
}
