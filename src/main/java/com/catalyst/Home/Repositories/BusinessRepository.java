package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.BusinessDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<BusinessDetails, Long> {
    Optional<BusinessDetails> findByUuid(String uuid);
    Optional<BusinessDetails> findByCompanyRegistrationNo(String companyRegistrationNo);
}
