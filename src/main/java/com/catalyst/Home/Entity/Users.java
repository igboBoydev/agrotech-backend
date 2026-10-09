package com.catalyst.Home.Entity;

import com.catalyst.Home.Utils.Role;
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
@Table(name = "users")
public class Users {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email")
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "account_non_locked")
    private Boolean accountNonLocked;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;
}
