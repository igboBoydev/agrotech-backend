package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.TokenValidation;
import com.catalyst.Home.Utils.ValidateType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface TokenValidationRepository extends JpaRepository<TokenValidation, Long> {
    Optional<TokenValidation> findTopByVerificationCodeAndVerificationTypeAndUsedFalseOrderByIdDesc(
            String verificationCode, ValidateType verificationType);

    @Transactional
    void deleteByEmail(String email);
}
