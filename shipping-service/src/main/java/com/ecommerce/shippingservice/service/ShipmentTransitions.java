package com.ecommerce.shippingservice.service;

import com.ecommerce.shippingservice.config.KafkaTopicsProperties;
import com.ecommerce.shippingservice.entity.Shipment;
import com.ecommerce.shippingservice.entity.ShipmentStatus;
import com.ecommerce.shippingservice.entity.TrackingEvent;
import com.ecommerce.shippingservice.event.EventTypes;
import com.ecommerce.shippingservice.event.ShipmentEvent;
import com.ecommerce.shippingservice.outbox.OutboxWriter;
import com.ecommerce.shippingservice.repository.ShipmentRepository;
import com.ecommerce.shippingservice.repository.TrackingEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The one place a shipment changes, so every change is recorded the same way, in the caller's transaction:
 * shipment saved and flushed (version incremented) -> tracking history line -> event in the outbox (for the
 * statuses that are announced, see {@link EventTypes#forStatus}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentTransitions {

    private final ShipmentRepository shipmentRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final OutboxWriter outboxWriter;
    private final KafkaTopicsProperties topics;

    /**
     * @param reason      for CANCELLED / DELIVERY_FAILED: kept as the shipment's statusReason and sent as the event's note
     * @param location    where it happened, if known
     * @param description the tracking history line
     * @param actorId     the admin; null for the system
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(Shipment shipment, ShipmentStatus next, String reason, String location, String description,
                      UUID actorId, Instant now) {
        ShipmentStatus from = shipment.getStatus();
        shipment.moveTo(next, reason, now);
        recorded(shipment, location, description, reason, actorId, now);
        log.info("Shipment {} {} -> {} by {}{}", shipment.getOrderNumber(), from, next,
                actorId == null ? "system" : actorId, location == null ? "" : " at " + location);
    }

    /** Saves a shipment whose status the caller already changed (e.g. {@link Shipment#ship}), with its history and event. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recorded(Shipment shipment, String location, String description, String note, UUID actorId, Instant now) {
        shipmentRepository.saveAndFlush(shipment);
        trackingEventRepository.save(new TrackingEvent(shipment.getId(), shipment.getStatus(), location, description,
                actorId, now));
        EventTypes.forStatus(shipment.getStatus()).ifPresent(type -> outboxWriter.write(topics.shipmentEvents(),
                shipment.getOrderId().toString(), type, ShipmentEvent.of(type, shipment, location, note, now)));
    }

    /** Adds a history line without changing the status (nothing is announced). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void note(Shipment shipment, String location, String description, UUID actorId, Instant now) {
        shipment.touch(now);
        shipmentRepository.saveAndFlush(shipment);
        trackingEventRepository.save(new TrackingEvent(shipment.getId(), shipment.getStatus(), location, description,
                actorId, now));
        log.info("Shipment {} note: {}", shipment.getOrderNumber(), description);
    }
}
