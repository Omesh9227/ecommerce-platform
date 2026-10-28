package com.ecommerce.wishlistservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** POST /api/v1/wishlist/items/{productId}/move-to-cart - the body is optional (quantity 1). */
public record MoveToCartRequest(
        @Schema(example = "1", description = "How many to put in the cart (default 1)")
        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = 100, message = "quantity must be at most 100")
        Integer quantity
) {

    public int quantityOrDefault() {
        return quantity == null ? 1 : quantity;
    }
}
