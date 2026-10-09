package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {
    Optional<LoanApplication> findByUuid(String uuid);
    List<LoanApplication> findByFarmers_Uuid(String farmerUuid);
}
