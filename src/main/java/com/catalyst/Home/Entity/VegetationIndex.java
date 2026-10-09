package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.DataSource;
import com.catalyst.Home.Utils.VegetationHealthBand;
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
@Table(name = "vegetation_indices")
public class VegetationIndex {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @ManyToOne
    @JoinColumn(name = "FARM_ID")
    private Farms farm;

    @Column(name = "NDVI_VALUE")
    private Double ndviValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "HEALTH_BAND")
    private VegetationHealthBand healthBand;

    @Enumerated(EnumType.STRING)
    @Column(name = "SOURCE")
    private DataSource source;

    @Column(name = "CAPTURED_AT")
    private String capturedAt;
}
