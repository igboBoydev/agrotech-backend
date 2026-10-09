package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.YieldPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface YieldPredictionRepository extends JpaRepository<YieldPrediction, Long> {
    Optional<YieldPrediction> findByUuid(String uuid);
    Optional<YieldPrediction> findTopByFarm_UuidOrderByIdDesc(String farmUuid);
    List<YieldPrediction> findByFarm_UuidOrderByIdDesc(String farmUuid);
}
