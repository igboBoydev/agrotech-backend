package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.SensorReading;
import com.catalyst.Home.Utils.SensorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {
    List<SensorReading> findByFarm_UuidAndSensorTypeOrderByIdDesc(String farmUuid, SensorType sensorType);
    Optional<SensorReading> findTopByFarm_UuidAndSensorTypeOrderByIdDesc(String farmUuid, SensorType sensorType);
}
