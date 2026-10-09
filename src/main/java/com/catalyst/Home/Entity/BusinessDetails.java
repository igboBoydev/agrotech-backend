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
@Table(name = "business")
public class BusinessDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "UUID")
    private String uuid;

    @Column(name = "COMPANY_NAME")
    private String companyName;

    @Column(name = "COMPANY_REGISTRATION_NO")
    private String companyRegistrationNo;

    @Column(name = "DATE_OF_REGISTRATION")
    private String dateOfRegistration;

    @Column(name = "COUNTRY_OF_REGISTRATION")
    private String countryOfRegistration;

    @Column(name = "COMPANY_ADDRESS")
    private String companyAddress;

    @Column(name = "STATE")
    private String state;

    @Column(name = "CITY")
    private String city;

    @Column(name = "COUNTRY")
    private String country;

    @Column(name = "LAST_NAME")
    private String lastName;

    @Column(name = "BVN")
    private String bvn;

    @Column(name = "TIN")
    private String tin;

    @Column(name = "ADDRESS")
    private String address;

    @OneToOne
    @JoinColumn(name = "USER_ID", unique = true)
    private Users user;

    @Column(name = "CREATED_AT")
    private String createdAt;
}
