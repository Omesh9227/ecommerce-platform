package com.ecommerce.recommendationservice.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The fields of order-service's order-events that recommendations need: who bought what, and when. */
public record OrderEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        UUID userId,
        List<Item> items
) {

    public record Item(UUID productId, int quantity) {
    }
}
