package com.ecommerce.notificationservice.repository;

import com.ecommerce.notificationservice.entity.OrderSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderSnapshotRepository extends JpaRepository<OrderSnapshot, UUID> {
}
