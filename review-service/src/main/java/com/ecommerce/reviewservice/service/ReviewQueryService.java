package com.ecommerce.reviewservice.service;

import com.ecommerce.reviewservice.dto.response.PageResponse;
import com.ecommerce.reviewservice.dto.response.ReviewResponse;
import com.ecommerce.reviewservice.entity.ReviewStatus;
import com.ecommerce.reviewservice.exception.BadRequestException;
import com.ecommerce.reviewservice.repository.ReviewRepository;
import com.ecommerce.reviewservice.repository.ReviewSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/** Reading reviews: a product's (public, visible only), my own, and all of them for admins. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {

    static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "rating", "helpfulCount");

    private final ReviewRepository reviewRepository;

    public PageResponse<ReviewResponse> forProduct(UUID productId, Integer rating, boolean verifiedOnly, Pageable pageable) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BadRequestException("rating must be between 1 and 5");
        }
        checkSort(pageable);
        return PageResponse.from(reviewRepository.findAll(
                ReviewSpecifications.withFilters(productId, ReviewStatus.VISIBLE, rating, verifiedOnly), pageable)
                .map(ReviewResponse::publicView));
    }

    public PageResponse<ReviewResponse> mine(UUID userId, Pageable pageable) {
        checkSort(pageable);
        return PageResponse.from(reviewRepository.findByUserId(userId, pageable).map(ReviewResponse::authorView));
    }

    public PageResponse<ReviewResponse> forAdmin(ReviewStatus status, UUID productId, Pageable pageable) {
        checkSort(pageable);
        return PageResponse.from(reviewRepository.findAll(
                ReviewSpecifications.withFilters(productId, status, null, false), pageable).map(ReviewResponse::adminView));
    }

    private static void checkSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'. Sortable fields: "
                        + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()));
            }
        }
    }
}
