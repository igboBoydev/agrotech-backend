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
@Table(name = "crop_diagnoses")
public class CropDiagnosis {
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

    @Column(name = "SYMPTOMS")
    private String symptoms;

    @Column(name = "DIAGNOSIS_RESULT")
    private String diagnosisResult;

    @Column(name = "CONFIDENCE")
    private Double confidence;

    @Column(name = "RECOMMENDED_ACTION")
    private String recommendedAction;

    @Column(name = "CREATED_AT")
    private String createdAt;
}
