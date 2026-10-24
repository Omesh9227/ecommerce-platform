package com.ecommerce.adminservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The fields of order-service's order-events that reports need (others are ignored). */
public record OrderEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        UUID userId,
        String status,
        String reason,
        BigDecimal totalAmount,
        String currency,
        List<Item> items,
        long orderVersion
) {

    public record Item(UUID productId, String sku, String productName, int quantity, BigDecimal unitPrice) {
    }
}
