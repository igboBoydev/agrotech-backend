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
@Table(name = "farms")
public class Farms {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ADDRESS")
    private String address;

    @Column(name = "LGA")
    private String lga;

    @Column(name = "STATE")
    private String state;

    @Column(name = "COUNTRY")
    private String country;

    @Column(name = "LAND_SIZE")
    private String landSize;

    @Column(name = "IS_LEASE")
    private Boolean isLease;

    @Column(name = "IS_OWN_PROPERTY")
    private Boolean isOwnProperty;

    @Column(name = "LEASE_YEARS")
    private Integer leaseYears;

    @Column(name = "NUM_OF_EMPLOYEES")
    private Integer numOfEmployees;

    @Column(name = "PLANT_TYPE")
    private String plantType;

    @Column(name = "PLANT_SEASON_START_DATE")
    private String plantSeasonStartDate;

    @Column(name = "PLANT_SEASON_END_DATE")
    private String plantSeasonEndDate;

    @Column(name = "FARM_TYPE")
    private String farmType;

    @Column(name = "UUID")
    private String uuid;

    // 🔹 Many Farms → One Business (or FarmersKyc depending on your design)
    @ManyToOne
    @JoinColumn(name = "FARMERS_ID")
    private FarmersDetails farmers;

    // 🔹 One Farm → Many Employees
    @OneToMany(mappedBy = "farm", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Employees> employees;

    // 🔹 One Farm → Many Documents
    @OneToMany(mappedBy = "farm", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Documents> farmDocuments;

    // 🔹 Farm Pictures (simple strings → URLs)
    @ElementCollection
    @CollectionTable(name = "FARM_PICTURES", joinColumns = @JoinColumn(name = "FARM_ID"))
    @Column(name = "PICTURE_URL")
    private List<String> farmPictures;
}
