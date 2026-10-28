package com.ecommerce.reviewservice.dto.response;

import com.ecommerce.reviewservice.entity.ProductRating;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A product's rating over its visible reviews, e.g. 4.3 from 120 reviews: {5: 70, 4: 30, 3: 10, 2: 5, 1: 5}. */
public record RatingSummaryResponse(
        UUID productId,
        @Schema(example = "4.3") BigDecimal averageRating,
        @Schema(example = "120") int reviewCount,
        @Schema(description = "Number of reviews per star rating, 5 stars first") Map<Integer, Integer> ratingCounts
) {

    public static RatingSummaryResponse of(ProductRating rating) {
        return new RatingSummaryResponse(rating.getProductId(), rating.getAverageRating(), rating.getReviewCount(),
                rating.countsByRating());
    }

    public static RatingSummaryResponse empty(UUID productId) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) {
            counts.put(stars, 0);
        }
        return new RatingSummaryResponse(productId, BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP), 0, counts);
    }
}
