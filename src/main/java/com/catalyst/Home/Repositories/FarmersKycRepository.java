package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.FarmersKyc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FarmersKycRepository extends JpaRepository<FarmersKyc, Long> {
}
