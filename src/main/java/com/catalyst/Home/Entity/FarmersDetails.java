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
@Table(name = "Farmers")
public class FarmersDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "FIRST_NAME")
    private String firstName;

    @Column(name = "LAST_NAME")
    private String lastName;

    @Column(name = "CONTACT_PHONE_NUMBER")
    private String contactPhoneNumber;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "OTHER_MAIDEN_NAME")
    private String otherMaidenName;

    @Column(name = "MIDDLE_NAME")
    private String middleName;

    @Column(name = "DATE_OF_BIRTH")
    private String dateOfBirth;
    @Column(name = "ACTIVE")
    private Boolean active;

    @Column(name = "SECTOR_CODE")
    private String sectorCode;

    @Column(name = "SEX")
    private String sex;

    @Column(name = "TITLE")
    private String title;

    @Column(name = "ALTERNATE_PHONE_NUMBER")
    private String alternatePhoneNumber;

    @Column(name = "NATIONALITY")
    private String nationality;

    @Column(name = "MARITAL_STATUS")
    private String maritalStatus;

    @Column(name = "EDUCATION")
    private String education;

    @Column(name = "NAME_OF_SPOUSE")
    private String nameOfSpouse;

    @Column(name = "BRANCH")
    private String branch;

    @Column(name = "FATHER_NAME")
    private String fatherName;

    @Column(name = "MOTHER_NAME")
    private String motherName;

    @Column(name = "USE_ALT_ACC_NUM")
    private Boolean useAltAccNum;

    @Column(name = "SMS_ENABLED")
    private Boolean smsEnabled;

    @Column(name = "EMAIL_ENABLED")
    private Boolean emailEnabled;

    @Column(name = "UUID")
    private String uuid;

    @Column(name = "EMAIL_VALIDATED")
    private Boolean emailValidated;

    @Column(name = "PHONE_VALIDATED")
    private Boolean phoneValidated;

    // 🔹 One-to-One (login credentials / identity)
    @OneToOne
    @JoinColumn(name = "USER_ID", unique = true)
    private Users user;

    // 🔹 One-to-One (KYC)
    @OneToOne
    @JoinColumn(name = "KYC_ID", unique = true)
    private FarmersKyc kyc;

    // 🔹 One-to-Many (Farms)
    @OneToMany(mappedBy = "farmers", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Farms> farms;

    @OneToMany(mappedBy = "farmers", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Employees> employees;

    @Column(name = "CREATED_AT")
    private String createdAt;

    @Column(name = "UPDATED_AT")
    private String updatedAt;

}
