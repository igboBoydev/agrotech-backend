package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.VerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "documents")
public class Documents {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DOCUMENT_NAME")
    private String documentName;

    @Column(name = "EXPIRATION_DATE")
    private LocalDate expirationDate;

    @Column(name = "DOCUMENT_URL")
    private String documentUrl;

    @Column(name = "DOCUMENT_NUMBER")
    private String documentNumber;

    @Column(name = "COUNTRY_OF_ISSUANCE")
    private String countryOfIssuance;

    @Column(name = "DATE_OF_ISSUANCE")
    private LocalDate dateOfIssuance;

    @Column(name = "PLACE_OF_ISSUANCE")
    private String placeOfIssuance;

    @Enumerated(EnumType.STRING)
    @Column(name = "VERIFICATION_STATUS")
    private VerificationStatus verificationStatus;

    @Column(name = "CREATED_AT")
    private String createdAt;

    @Column(name = "UUID")
    private String uuid;

    // 🔹 Many Documents → One KYC
    @ManyToOne
    @JoinColumn(name = "KYC_ID")
    private FarmersKyc kyc;

    // 🔹 Many Documents → One Farm
    @ManyToOne
    @JoinColumn(name = "FARM_ID")
    private Farms farm;
}
