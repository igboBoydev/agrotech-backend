package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.VegetationIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VegetationIndexRepository extends JpaRepository<VegetationIndex, Long> {
    List<VegetationIndex> findByFarm_UuidOrderByIdDesc(String farmUuid);
    Optional<VegetationIndex> findTopByFarm_UuidOrderByIdDesc(String farmUuid);
}
