package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.CreditScoreRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CreditScoreRecordRepository extends JpaRepository<CreditScoreRecord, Long> {
    Optional<CreditScoreRecord> findByUuid(String uuid);
    Optional<CreditScoreRecord> findTopByFarmers_UuidOrderByIdDesc(String farmerUuid);
}
