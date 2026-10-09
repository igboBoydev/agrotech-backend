package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.Farms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FarmsRepository extends JpaRepository<Farms, Long> {
    Optional<Farms> findByUuid(String uuid);
    List<Farms> findByFarmers_Uuid(String farmerUuid);
    List<Farms> findByStateAndPlantType(String state, String plantType);
    List<Farms> findByState(String state);
}
