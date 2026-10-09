package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.ValidateType;
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
@Table(name = "token_validation")
public class TokenValidation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "email")
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "verification_code")
    private String verificationCode;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "verification_type")
    private ValidateType verificationType;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "expiry")
    private String expiry;

    @Builder.Default
    @Column(name = "expired")
    private Boolean expired = false;

    @Builder.Default
    @Column(name = "used")
    private Boolean used = false;

    @Column(name = "created_at")
    private String createdAt;

    @Column(name = "updated_at")
    private String updatedAt;
}
