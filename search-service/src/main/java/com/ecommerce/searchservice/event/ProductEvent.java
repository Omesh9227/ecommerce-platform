package com.ecommerce.searchservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The fields of product-service's product-events that search needs. brand, description, categoryId, discountPercent,
 * imageUrls and createdAt exist since Phase 12; older events don't have them (until the catalog is republished).
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
        String brand,
        String description,
        UUID categoryId,
        BigDecimal discountPercent,
        List<String> imageUrls,
        Instant createdAt,
        long productVersion
) {
}
