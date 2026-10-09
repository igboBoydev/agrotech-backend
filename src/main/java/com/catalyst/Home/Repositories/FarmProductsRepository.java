package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.FarmProducts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FarmProductsRepository extends JpaRepository<FarmProducts, Long> {
    List<FarmProducts> findByFarm_Uuid(String farmUuid);
}
