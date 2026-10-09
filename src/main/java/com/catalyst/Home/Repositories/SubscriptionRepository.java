package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByUuid(String uuid);
    Optional<Subscription> findTopByBusinessIdOrderByIdDesc(String businessId);
}
