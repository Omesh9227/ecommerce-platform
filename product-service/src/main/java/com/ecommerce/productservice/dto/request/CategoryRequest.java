package com.ecommerce.productservice.dto.request;

import com.ecommerce.productservice.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Create or update a category. Omit parentId for a top-level category.")
public record CategoryRequest(

        @Schema(example = "Mobiles")
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
        @Pattern(regexp = ValidationPatterns.CATEGORY_NAME, message = "Name contains invalid characters")
        String name,

        @Schema(example = "Smartphones and feature phones")
        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        @Schema(description = "Parent category id; null for a top-level category")
        UUID parentId
) {
}
