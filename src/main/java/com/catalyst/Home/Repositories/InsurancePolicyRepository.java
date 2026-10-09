package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.InsurancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {
    Optional<InsurancePolicy> findByUuid(String uuid);
    List<InsurancePolicy> findByFarmers_Uuid(String farmerUuid);
}
