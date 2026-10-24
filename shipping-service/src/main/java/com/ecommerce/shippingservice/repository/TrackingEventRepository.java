package com.ecommerce.shippingservice.repository;

import com.ecommerce.shippingservice.entity.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, UUID> {

    /** Oldest first, as a tracking page lists them (ties: the order they were recorded). */
    List<TrackingEvent> findByShipmentIdOrderByOccurredAtAscSeqAsc(UUID shipmentId);

    Optional<TrackingEvent> findFirstByShipmentIdOrderByOccurredAtDescSeqDesc(UUID shipmentId);
}
