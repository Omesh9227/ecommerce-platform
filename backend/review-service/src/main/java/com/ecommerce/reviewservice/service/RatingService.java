package com.ecommerce.reviewservice.service;

import com.ecommerce.reviewservice.config.KafkaTopicsProperties;
import com.ecommerce.reviewservice.dto.response.RatingSummaryResponse;
import com.ecommerce.reviewservice.entity.ProductRating;
import com.ecommerce.reviewservice.entity.ReviewStatus;
import com.ecommerce.reviewservice.event.EventTypes;
import com.ecommerce.reviewservice.event.ReviewEvent;
import com.ecommerce.reviewservice.outbox.OutboxWriter;
import com.ecommerce.reviewservice.repository.ProductRatingRepository;
import com.ecommerce.reviewservice.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps each product's rating summary (average, count, stars breakdown) in step with its visible reviews, and tells
 * product-service about every change (PRODUCT_RATING_UPDATED on review-events, via the outbox).
 *
 * The summary row is locked while it is recalculated, so two reviews written at the same moment are both counted:
 * the second recalculation waits, then sees both.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private final ProductRatingRepository ratingRepository;
    private final ReviewRepository reviewRepository;
    private final OutboxWriter outboxWriter;
    private final KafkaTopicsProperties topics;
    private final Clock clock;

    /** Call inside the transaction that changed a review of this product, after the change. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProductRating recalculate(UUID productId) {
        Instant now = clock.instant();
        ratingRepository.createIfMissing(productId, now);
        ProductRating rating = ratingRepository.lockById(productId)
                .orElseThrow(() -> new IllegalStateException("Rating row for product " + productId + " is missing"));

        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : reviewRepository.countByRating(productId, ReviewStatus.VISIBLE)) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        rating.recalculate(counts, now);

        outboxWriter.write(topics.reviewEvents(), productId.toString(), EventTypes.PRODUCT_RATING_UPDATED,
                ReviewEvent.ratingUpdated(rating, now));
        log.debug("Product {} rating {} from {} review(s) (v{})", productId, rating.getAverageRating(),
                rating.getReviewCount(), rating.getRatingVersion());
        return rating;
    }

    @Transactional(readOnly = true)
    public RatingSummaryResponse summary(UUID productId) {
        return ratingRepository.findById(productId)
                .map(RatingSummaryResponse::of)
                .orElseGet(() -> RatingSummaryResponse.empty(productId));
    }
}
