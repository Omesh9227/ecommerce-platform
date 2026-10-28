package com.ecommerce.inventoryservice.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumed from product-events (published by product-service).
 * productVersion increases with every change to the product, so older events can be ignored.
 * Unknown JSON fields are ignored (Spring Boot's ObjectMapper default), so product-service can add fields freely.
 */
public record ProductEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID productId,
        String sku,
        String name,
        String status,
        long productVersion
) {
}
