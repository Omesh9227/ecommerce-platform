package com.ecommerce.productservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The fields of review-service's review-events that product-service needs (others, like the stars breakdown,
 * are ignored). ratingVersion only goes up per product.
 */
public record ReviewEvent(
        UUID eventId,
        String eventType,
        UUID productId,
        BigDecimal averageRating,
        int reviewCount,
        long ratingVersion
) {
}
