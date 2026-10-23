package com.ecommerce.reviewservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin: why a review is hidden (shown to its author). */
public record HideReviewRequest(
        @Schema(example = "Contains personal contact details")
        @NotBlank(message = "reason is required")
        @Size(max = 300, message = "reason must be at most 300 characters")
        String reason
) {
}
