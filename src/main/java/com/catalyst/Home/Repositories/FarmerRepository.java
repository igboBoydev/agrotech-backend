package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.FarmersDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FarmerRepository extends JpaRepository<FarmersDetails, Long> {
    Optional<FarmersDetails> findByEmail(String email);
    Optional<FarmersDetails> findByContactPhoneNumber(String contactPhoneNumber);
    Optional<FarmersDetails> findByUuid(String uuid);
}
