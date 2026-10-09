package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.GpsLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GpsLocationRepository extends JpaRepository<GpsLocation, Long> {
    List<GpsLocation> findByFarm_UuidOrderByIdDesc(String farmUuid);
    Optional<GpsLocation> findTopByFarm_UuidOrderByIdDesc(String farmUuid);
}
