package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.VerificationStatus;
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
@Table(name = "address")
public class FarmersAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "RESIDENTIAL_ADDRESS")
    private String residentialAddress;

    @Column(name = "RESIDENCY")
    private String residency;

    @Column(name = "STATE_OF_ORIGIN")
    private String stateOfOrigin;

    @Column(name = "LGA_OF_ORIGIN")
    private String lgaOfOrigin;

    @Column(name = "CITY")
    private String city;

    @Column(name = "POSTAL_CODE")
    private String postalCode;

    @Column(name = "STATE")
    private String state;

    @Column(name = "COUNTRY")
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "VERIFICATION_STATUS")
    private VerificationStatus verificationStatus;

    @Column(name = "UUID")
    private String uuid;
}
