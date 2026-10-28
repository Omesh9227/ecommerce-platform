package com.ecommerce.reviewservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A product's rating summary over its visible reviews. Recalculated (under a row lock) after every change to one of
 * its reviews; ratingVersion goes up each time, so product-service can ignore old or repeated updates.
 */
@Entity
@Table(name = "product_ratings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRating {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "average_rating", nullable = false, precision = 2, scale = 1)
    private BigDecimal averageRating;

    @Column(name = "count_1", nullable = false)
    private int count1;

    @Column(name = "count_2", nullable = false)
    private int count2;

    @Column(name = "count_3", nullable = false)
    private int count3;

    @Column(name = "count_4", nullable = false)
    private int count4;

    @Column(name = "count_5", nullable = false)
    private int count5;

    @Column(name = "rating_version", nullable = false)
    private long ratingVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * @param countsByRating how many visible reviews have 1, 2, ... 5 stars (missing = 0)
     */
    public void recalculate(Map<Integer, Long> countsByRating, Instant now) {
        count1 = count(countsByRating, 1);
        count2 = count(countsByRating, 2);
        count3 = count(countsByRating, 3);
        count4 = count(countsByRating, 4);
        count5 = count(countsByRating, 5);
        reviewCount = count1 + count2 + count3 + count4 + count5;
        averageRating = average(count1, count2, count3, count4, count5);
        ratingVersion++;
        updatedAt = now;
    }

    /** One decimal, rounded half up: 4.25 -> 4.3. 0.0 when there are no reviews. */
    static BigDecimal average(int c1, int c2, int c3, int c4, int c5) {
        int total = c1 + c2 + c3 + c4 + c5;
        if (total == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        long stars = c1 + 2L * c2 + 3L * c3 + 4L * c4 + 5L * c5;
        return BigDecimal.valueOf(stars).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private static int count(Map<Integer, Long> counts, int rating) {
        return Math.toIntExact(counts.getOrDefault(rating, 0L));
    }

    /** Counts per rating, 5 stars first. */
    public Map<Integer, Integer> countsByRating() {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        counts.put(5, count5);
        counts.put(4, count4);
        counts.put(3, count3);
        counts.put(2, count2);
        counts.put(1, count1);
        return counts;
    }
}
