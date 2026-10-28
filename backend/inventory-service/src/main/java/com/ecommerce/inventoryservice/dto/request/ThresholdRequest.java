package com.ecommerce.inventoryservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "Alert when available stock drops to this level or below")
public record ThresholdRequest(

        @Schema(example = "10")
        @NotNull(message = "lowStockThreshold is required")
        @PositiveOrZero(message = "lowStockThreshold cannot be negative")
        @Max(value = 1_000_000, message = "lowStockThreshold must be at most 1,000,000")
        Integer lowStockThreshold
) {
}
