package com.ecommerce.reviewservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Writing or editing a review. */
public record ReviewRequest(
        @Schema(example = "5", description = "1 to 5 stars")
        @NotNull(message = "rating is required")
        @Min(value = 1, message = "rating must be between 1 and 5")
        @Max(value = 5, message = "rating must be between 1 and 5")
        Integer rating,

        @Schema(example = "Great camera, average battery")
        @NotBlank(message = "title is required")
        @Size(max = 150, message = "title must be at most 150 characters")
        String title,

        @Schema(example = "Photos are excellent in daylight. The battery lasts a day with moderate use.")
        @Size(max = 4000, message = "comment must be at most 4000 characters")
        String comment
) {

    public String trimmedTitle() {
        return title.strip();
    }

    /** Blank comments are stored as none. */
    public String trimmedComment() {
        return comment == null || comment.isBlank() ? null : comment.strip();
    }
}
