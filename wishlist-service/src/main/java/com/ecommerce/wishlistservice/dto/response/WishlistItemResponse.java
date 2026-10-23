package com.ecommerce.wishlistservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One saved product with its live price and stock.
 *
 * @param price           what it costs now (the saved price if the catalog can't be reached)
 * @param listPrice       price before discount, null if unknown
 * @param priceWhenAdded  what it cost when it was saved
 * @param priceDifference price - priceWhenAdded: negative = cheaper than when saved
 * @param priceDropped    true when it's cheaper now than when it was saved
 */
public record WishlistItemResponse(
        UUID productId,
        String sku,
        String name,
        String imageUrl,
        @Schema(example = "53999.10") BigDecimal price,
        @Schema(example = "59999.00") BigDecimal listPrice,
        @Schema(example = "55999.00") BigDecimal priceWhenAdded,
        @Schema(example = "-1999.90") BigDecimal priceDifference,
        boolean priceDropped,
        Integer availableQuantity,
        WishlistItemStatus status,
        @Schema(example = "Price dropped from 55999.00 to 53999.10") String message,
        Instant addedAt
) {
}
