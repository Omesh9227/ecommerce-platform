package com.ecommerce.inventoryservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Add stock (positive) or remove stock (negative), e.g. a delivery or damaged goods")
public record AdjustStockRequest(

        @Schema(example = "50", description = "Positive = restock, negative = removal. Cannot be 0.")
        @NotNull(message = "quantityChange is required")
        @Min(value = -1_000_000, message = "quantityChange must be at least -1,000,000")
        @Max(value = 1_000_000, message = "quantityChange must be at most 1,000,000")
        Integer quantityChange,

        @Schema(example = "Supplier delivery #4521")
        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason
) {
}
