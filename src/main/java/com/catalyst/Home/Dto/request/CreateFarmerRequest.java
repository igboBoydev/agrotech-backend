package com.catalyst.Home.Dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFarmerRequest {
    private String firstName;
    private String lastName;
    private String contactPhoneNumber;
    private String email;
    private String motherMaidenName;
    private String middleName;
    private String dateOfBirth;
    private String createdAt;
    private String updatedAt;
    private String rimNumber;
    private String sex;
    private String title;
    private String alternatePhoneNumber;
    private String nationality;
    private String maritalStatus;
    private String education;
    private String nameOfSpouse;
    private String branch;
    private String fatherName;
    private String motherName;
}
