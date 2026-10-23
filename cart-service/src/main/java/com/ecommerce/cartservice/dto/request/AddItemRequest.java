package com.ecommerce.cartservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Add a product to the cart. If it is already there, the quantity is increased.")
public record AddItemRequest(

        @NotNull(message = "productId is required")
        UUID productId,

        @Schema(example = "1", description = "Units to add (the per-product limit and stock are checked too)")
        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = 100, message = "quantity must be at most 100")
        Integer quantity
) {
}
