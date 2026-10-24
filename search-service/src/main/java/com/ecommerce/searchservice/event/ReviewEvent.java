package com.ecommerce.searchservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/** The fields of review-service's review-events that search needs (rating filter and "best rated" sort). */
public record ReviewEvent(
        UUID eventId,
        String eventType,
        UUID productId,
        BigDecimal averageRating,
        int reviewCount,
        long ratingVersion
) {
}
