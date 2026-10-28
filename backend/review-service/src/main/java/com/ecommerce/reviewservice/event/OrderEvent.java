package com.ecommerce.reviewservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The fields of order-service's order-events that reviews need (others are ignored). */
public record OrderEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        UUID userId,
        List<Item> items
) {

    public record Item(UUID productId, String sku, String productName, int quantity, BigDecimal unitPrice) {
    }
}
