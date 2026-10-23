package com.ecommerce.productservice.event;

import com.ecommerce.productservice.entity.Product;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on product-events for PRODUCT_CREATED / PRODUCT_UPDATED / PRODUCT_DISCONTINUED.
 * productVersion increases with every change, so consumers can ignore out-of-date events.
 * (V2 migration builds the same JSON shape in SQL for the backfill - keep them in sync.)
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

    public static ProductEvent of(String eventType, Product product) {
        return new ProductEvent(
                UUID.randomUUID(),
                eventType,
                Instant.now(),
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getStatus().name(),
                product.getVersion() == null ? 0 : product.getVersion()
        );
    }
}
