package com.ecommerce.shippingservice.event;

import com.ecommerce.shippingservice.entity.Shipment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published on shipment-events (key = order id, so a shipment's events stay in order with its order's).
 * order-service moves the order along (SHIPPED, DELIVERED) and notification-service emails the customer.
 * shipmentVersion increases with every change: consumers ignore events older than the last one they applied.
 *
 * @param location where it happened (e.g. "Bengaluru hub"), if known
 * @param note     the reason for a failed delivery attempt, or a carrier's remark
 */
public record ShipmentEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID shipmentId,
        UUID orderId,
        String orderNumber,
        UUID userId,
        String userEmail,
        String status,
        String carrier,
        String trackingNumber,
        LocalDate estimatedDeliveryDate,
        String location,
        String note,
        int failedAttempts,
        long shipmentVersion
) {

    public static ShipmentEvent of(String eventType, Shipment s, String location, String note, Instant now) {
        return new ShipmentEvent(UUID.randomUUID(), eventType, now, s.getId(), s.getOrderId(), s.getOrderNumber(),
                s.getUserId(), s.getUserEmail(), s.getStatus().name(), s.getCarrier(), s.getTrackingNumber(),
                s.getEstimatedDeliveryDate(), location, note, s.getFailedAttempts(), s.currentVersion());
    }
}
