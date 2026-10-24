package com.ecommerce.adminservice.event;

import java.time.Instant;
import java.util.UUID;

/** The fields of shipping-service's shipment-events that reports need (missed deliveries). */
public record ShipmentEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        String carrier,
        String trackingNumber,
        String note,
        int failedAttempts
) {
}
