package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.InsuranceClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InsuranceClaimRepository extends JpaRepository<InsuranceClaim, Long> {
    Optional<InsuranceClaim> findByUuid(String uuid);
}
