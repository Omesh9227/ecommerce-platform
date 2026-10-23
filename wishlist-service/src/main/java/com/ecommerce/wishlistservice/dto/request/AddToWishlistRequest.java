package com.ecommerce.wishlistservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** POST /api/v1/wishlist/items */
public record AddToWishlistRequest(
        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull(message = "productId is required")
        UUID productId
) {
}
