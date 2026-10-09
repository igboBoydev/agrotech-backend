package com.catalyst.Home.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "Farmers_kyc")
public class FarmersKyc {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    // 🔹 One KYC → Many Documents
    @OneToMany(mappedBy = "kyc", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Documents> documentsAndIdentity;

    // 🔹 One KYC → One Address
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "ADDRESS_ID")
    private FarmersAddress individualFarmerAddress;

    @Column(name = "SIGNATURE")
    private String signature;

    @Column(name = "BVN")
    private String bvn;

    @Column(name = "PASSPORT_PHOTOGRAPH")
    private String passportPhotograph;

    @Column(name = "KYC_LEVEL")
    private String kycLevel;

    @Column(name = "TIN")
    private String tin;

    @Column(name = "BVN_VERIFIED")
    private Boolean bvnVerified;

    @Column(name = "NIN")
    private String nin;

    @Column(name = "NIN_VERIFIED")
    private Boolean ninVerified;

    @Column(name = "TIN_VERIFIED")
    private Boolean tinVerified;

    @Column(name = "PASSPORT_PHOTOGRAPH_VERIFIED")
    private Boolean passportPhotographVerified;

    @Column(name = "TIER")
    private String tier;

    @Column(name = "DRIVING_LICENSE")
    private String drivingLicense;

    @Column(name = "DRIVING_LICENSE_VERIFIED")
    private Boolean drivingLicenseVerified;

    @Column(name = "INTERNATIONAL_PASSPORT_NUMBER")
    private String internationalPassportNumber;

    @Column(name = "INTERNATIONAL_PASSPORT_VERIFIED")
    private Boolean internationalPassportVerified;

    @Column(name = "VOTERS_CARD_NUMBER")
    private String votersCardNumber;

    @Column(name = "UUID")
    private String uuid;
}
