package com.ecommerce.productservice.service;

import com.ecommerce.productservice.event.EventTypes;
import com.ecommerce.productservice.event.ReviewEvent;
import com.ecommerce.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Keeps each product's averageRating / reviewCount in step with review-service, so product pages and lists can
 * show and sort by rating without calling review-service. Older or repeated events are ignored (ratingVersion
 * check in the UPDATE).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRatingService {

    private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5);

    private final ProductRepository productRepository;

    @Transactional
    public void apply(ReviewEvent event) {
        if (!EventTypes.PRODUCT_RATING_UPDATED.equals(event.eventType())) {
            return;
        }
        if (event.productId() == null || event.averageRating() == null || event.reviewCount() < 0
                || event.averageRating().signum() < 0 || event.averageRating().compareTo(MAX_RATING) > 0) {
            throw new IllegalArgumentException("Review event " + event.eventId() + " has invalid fields");
        }

        int rows = productRepository.updateRating(event.productId(), event.averageRating(), event.reviewCount(),
                event.ratingVersion());
        if (rows == 1) {
            log.debug("Rating of product {} is now {} from {} review(s) (v{})", event.productId(),
                    event.averageRating(), event.reviewCount(), event.ratingVersion());
        } else {
            log.debug("Ignored review event for product {} (unknown product or outdated version {})",
                    event.productId(), event.ratingVersion());
        }
    }
}
