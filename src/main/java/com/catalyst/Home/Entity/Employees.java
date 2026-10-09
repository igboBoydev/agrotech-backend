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
@Table(name = "employees")
public class Employees {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "FIRST_NAME")
    private String firstName;

    @Column(name = "LAST_NAME")
    private String lastName;

    @Column(name = "ACTIVE")
    private Boolean active;

    @Column(name = "UUID")
    private String uuid;

    @Column(name = "OCCUPATION")
    private String occupation;

    // 🔹 Many Employees → One Farmer (Customer)
    @ManyToOne
    @JoinColumn(name = "FARMER_ID")
    private FarmersDetails farmers;

    @Column(name = "BUSINESS_CATEGORY")
    private String businessCategory;

    @Column(name = "EMPLOYMENT_CATEGORY")
    private String employmentCategory;

    @Column(name = "START_DATE")
    private String startDate;

    @Column(name = "END_DATE")
    private String endDate;

    @Column(name = "DURATION_OF_EMPLOYMENT")
    private String durationOfEmployment;

    @Column(name = "EMPLOYER_PHONE")
    private String employerPhone;

    @Column(name = "POSITION")
    private String position;

    // 🔹 Optional: If Employee belongs to a Farm
    @ManyToOne
    @JoinColumn(name = "FARM_ID")
    private Farms farm;
}
