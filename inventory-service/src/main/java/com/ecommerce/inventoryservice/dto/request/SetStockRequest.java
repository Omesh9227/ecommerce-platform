package com.ecommerce.inventoryservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Set the on-hand quantity to an exact number (e.g. after a stock count)")
public record SetStockRequest(

        @Schema(example = "120")
        @NotNull(message = "quantityOnHand is required")
        @PositiveOrZero(message = "quantityOnHand cannot be negative")
        @Max(value = 1_000_000, message = "quantityOnHand must be at most 1,000,000")
        Integer quantityOnHand,

        @Schema(example = "Monthly stock count")
        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason
) {
}
