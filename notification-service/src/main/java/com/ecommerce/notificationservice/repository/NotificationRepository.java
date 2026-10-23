package com.ecommerce.notificationservice.repository;

import com.ecommerce.notificationservice.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByEventId(UUID eventId);

    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    Page<Notification> findByOrderNumber(String orderNumber, Pageable pageable);
}
