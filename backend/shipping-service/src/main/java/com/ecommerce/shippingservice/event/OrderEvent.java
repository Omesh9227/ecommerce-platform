package com.ecommerce.shippingservice.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The fields of order-service's order-events that shipping needs (others are ignored).
 * shippingAddress is sent since Phase 11; older events don't have it.
 */
public record OrderEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        UUID userId,
        String userEmail,
        String reason,
        List<Item> items,
        Address shippingAddress,
        long orderVersion
) {

    public record Item(UUID productId, String sku, String productName, int quantity) {
    }

    public record Address(String fullName, String phone, String line1, String line2, String city, String state,
                          String postalCode, String country) {
    }
}
