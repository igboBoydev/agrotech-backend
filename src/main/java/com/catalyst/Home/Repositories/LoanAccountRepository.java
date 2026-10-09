package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.LoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {
    Optional<LoanAccount> findByUuid(String uuid);
    Optional<LoanAccount> findByApplication_Uuid(String applicationUuid);
    List<LoanAccount> findByFarmers_Uuid(String farmerUuid);
}
