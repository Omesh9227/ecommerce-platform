package com.ecommerce.reviewservice.dto.response;

import com.ecommerce.reviewservice.entity.Review;
import com.ecommerce.reviewservice.entity.ReviewStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * A review. userId is only included for admins, hiddenReason only for the author and admins; the public never sees
 * who wrote a review beyond reviewerName.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewResponse(
        UUID id,
        UUID productId,
        @Schema(example = "Omesh K.") String reviewerName,
        @Schema(example = "5") int rating,
        String title,
        String comment,
        @Schema(description = "The reviewer received this product (a delivered order)") boolean verifiedPurchase,
        int helpfulCount,
        ReviewStatus status,
        String hiddenReason,
        Instant createdAt,
        @Schema(description = "When the author last changed it; absent if never") Instant editedAt,
        UUID userId
) {

    /** As anyone may see it. */
    public static ReviewResponse publicView(Review r) {
        return of(r, false, false);
    }

    /** As its author sees it (with the reason if an admin hid it). */
    public static ReviewResponse authorView(Review r) {
        return of(r, true, false);
    }

    public static ReviewResponse adminView(Review r) {
        return of(r, true, true);
    }

    private static ReviewResponse of(Review r, boolean withReason, boolean withUser) {
        return new ReviewResponse(r.getId(), r.getProductId(), r.getReviewerName(), r.getRating(), r.getTitle(),
                r.getComment(), r.isVerifiedPurchase(), r.getHelpfulCount(), r.getStatus(),
                withReason ? r.getHiddenReason() : null, r.getCreatedAt(), r.getEditedAt(), withUser ? r.getUserId() : null);
    }
}
