package com.ecommerce.recommendationservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/** The fields of review-service's review-events that recommendations need (better rated products are recommended first). */
public record ReviewEvent(
        UUID eventId,
        String eventType,
        UUID productId,
        BigDecimal averageRating,
        int reviewCount,
        long ratingVersion
) {
}
