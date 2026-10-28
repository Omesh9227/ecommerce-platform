package com.ecommerce.reviewservice.repository;

import com.ecommerce.reviewservice.entity.Review;
import com.ecommerce.reviewservice.entity.ReviewStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** WHERE clauses for review lists. Null filters are ignored. */
public final class ReviewSpecifications {

    private ReviewSpecifications() {
    }

    public static Specification<Review> withFilters(UUID productId, ReviewStatus status, Integer rating,
                                                    boolean verifiedOnly) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (productId != null) {
                predicates.add(cb.equal(root.get("productId"), productId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (rating != null) {
                predicates.add(cb.equal(root.get("rating"), rating));
            }
            if (verifiedOnly) {
                predicates.add(cb.isTrue(root.get("verifiedPurchase")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
