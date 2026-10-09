package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.MfbReferral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MfbReferralRepository extends JpaRepository<MfbReferral, Long> {
    Optional<MfbReferral> findByUuid(String uuid);
    List<MfbReferral> findByFarmers_Uuid(String farmerUuid);
}
