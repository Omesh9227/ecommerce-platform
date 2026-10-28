package com.ecommerce.orderservice.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The fields of shipping-service's shipment-events that orders need (others are ignored).
 * shipmentVersion increases with every change to the shipment; an event not newer than the last one applied is ignored.
 */
public record ShipmentEvent(
        UUID eventId,
        String eventType,
        UUID orderId,
        String orderNumber,
        String carrier,
        String trackingNumber,
        LocalDate estimatedDeliveryDate,
        String location,
        String note,
        long shipmentVersion
) {
}
