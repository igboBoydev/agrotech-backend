package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.CropDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CropDiagnosisRepository extends JpaRepository<CropDiagnosis, Long> {
    Optional<CropDiagnosis> findByUuid(String uuid);
    List<CropDiagnosis> findByFarm_UuidOrderByIdDesc(String farmUuid);
}
