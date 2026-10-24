package com.ecommerce.shippingservice.repository;

import com.ecommerce.shippingservice.entity.Shipment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, UUID>, JpaSpecificationExecutor<Shipment> {

    Optional<Shipment> findByOrderNumber(String orderNumber);

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    boolean existsByOrderId(UUID orderId);

    boolean existsByTrackingNumber(String trackingNumber);

    /** Locks the shipment row for this transaction: a warehouse action, a carrier scan and a cancellation never overlap. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shipment s WHERE s.id = :id")
    Optional<Shipment> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shipment s WHERE s.orderId = :orderId")
    Optional<Shipment> findByOrderIdForUpdate(@Param("orderId") UUID orderId);
}
