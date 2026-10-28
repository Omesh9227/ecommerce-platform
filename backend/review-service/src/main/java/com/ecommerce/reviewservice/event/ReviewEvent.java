package com.ecommerce.reviewservice.event;

import com.ecommerce.reviewservice.entity.ProductRating;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published on review-events (key = product id) after every change to a product's reviews.
 * product-service keeps averageRating / reviewCount on the product from it; ratingVersion only goes up.
 */
public record ReviewEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID productId,
        BigDecimal averageRating,
        int reviewCount,
        Map<Integer, Integer> ratingCounts,
        long ratingVersion
) {

    public static ReviewEvent ratingUpdated(ProductRating rating, Instant now) {
        return new ReviewEvent(UUID.randomUUID(), EventTypes.PRODUCT_RATING_UPDATED, now, rating.getProductId(),
                rating.getAverageRating(), rating.getReviewCount(), rating.countsByRating(), rating.getRatingVersion());
    }
}
