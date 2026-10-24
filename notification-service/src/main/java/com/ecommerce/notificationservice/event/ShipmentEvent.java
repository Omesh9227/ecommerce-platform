package com.ecommerce.notificationservice.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The fields of shipping-service's shipment-events that emails need (others are ignored).
 *
 * @param note the reason a delivery attempt failed
 */
public record ShipmentEvent(
        UUID eventId,
        String eventType,
        UUID orderId,
        String orderNumber,
        UUID userId,
        String userEmail,
        String carrier,
        String trackingNumber,
        LocalDate estimatedDeliveryDate,
        String note,
        int failedAttempts
) {
}
