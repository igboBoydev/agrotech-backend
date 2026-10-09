package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.DataSource;
import com.catalyst.Home.Utils.SensorType;
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
@Table(name = "sensor_readings")
public class SensorReading {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @ManyToOne
    @JoinColumn(name = "FARM_ID")
    private Farms farm;

    @Enumerated(EnumType.STRING)
    @Column(name = "SENSOR_TYPE")
    private SensorType sensorType;

    @Column(name = "VALUE")
    private Double value;

    @Column(name = "UNIT")
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "SOURCE")
    private DataSource source;

    @Column(name = "RECORDED_AT")
    private String recordedAt;
}
