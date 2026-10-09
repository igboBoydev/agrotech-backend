package com.catalyst.Home.Repositories;

import com.catalyst.Home.Entity.Notification;
import com.catalyst.Home.Utils.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByUuid(String uuid);
    List<Notification> findByRecipientIdOrderByIdDesc(String recipientId);
    long countByRecipientIdAndStatus(String recipientId, NotificationStatus status);
}
