package com.ecommerce.notificationservice.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** The fields of order-service's events that notifications need (other fields are ignored). */
public record OrderEvent(
        UUID eventId,
        String eventType,
        UUID orderId,
        String orderNumber,
        UUID userId,
        String userEmail,
        String status,
        String reason,
        String carrier,
        String trackingNumber,
        BigDecimal totalAmount,
        String currency,
        List<Item> items,
        long orderVersion
) {

    public record Item(UUID productId, String sku, String productName, int quantity, BigDecimal unitPrice) {
    }
}
