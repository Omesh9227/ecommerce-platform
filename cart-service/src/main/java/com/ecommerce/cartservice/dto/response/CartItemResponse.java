package com.ecommerce.cartservice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of the cart.
 *
 * @param unitPrice         current selling price (or the price when added, if it can't be refreshed)
 * @param listPrice         current price before discount (null if unknown)
 * @param priceWhenAdded    selling price when the item was (last) added
 * @param priceChanged      true if the current price differs from priceWhenAdded
 * @param availableQuantity units in stock right now (null if unknown or not for sale)
 * @param message           short explanation for anything that needs the shopper's attention
 */
public record CartItemResponse(
        UUID productId,
        String sku,
        String name,
        String imageUrl,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal listPrice,
        BigDecimal priceWhenAdded,
        boolean priceChanged,
        BigDecimal lineTotal,
        Integer availableQuantity,
        CartItemStatus status,
        String message,
        Instant addedAt
) {
}
