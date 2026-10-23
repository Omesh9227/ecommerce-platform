package com.ecommerce.productservice.event;

import com.ecommerce.productservice.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on product-events for PRODUCT_CREATED / PRODUCT_UPDATED / PRODUCT_DISCONTINUED.
 * productVersion increases with every change, so consumers can ignore out-of-date events.
 * price / sellingPrice (list price and price after discount) let wishlist-service notice price drops.
 * (V2 migration built older events in SQL for the backfill, without prices - consumers accept both.)
 */
public record ProductEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID productId,
        String sku,
        String name,
        String status,
        BigDecimal price,
        BigDecimal sellingPrice,
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
                product.getPrice(),
                product.getSellingPrice(),
                product.getVersion() == null ? 0 : product.getVersion()
        );
    }
}
