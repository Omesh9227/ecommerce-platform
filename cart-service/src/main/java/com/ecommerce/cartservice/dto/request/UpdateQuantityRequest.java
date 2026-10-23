package com.ecommerce.cartservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Change how many units of a product are in the cart. To remove it, use DELETE.")
public record UpdateQuantityRequest(

        @Schema(example = "2")
        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1 (use DELETE to remove the item)")
        @Max(value = 100, message = "quantity must be at most 100")
        Integer quantity
) {
}
