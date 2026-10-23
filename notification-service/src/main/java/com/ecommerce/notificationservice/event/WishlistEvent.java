package com.ecommerce.notificationservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The fields of wishlist-service's wishlist-events that emails need (others are ignored).
 *
 * @param previousPrice the price the customer last saw (when they saved it, or in our previous price-drop email)
 */
public record WishlistEvent(
        UUID eventId,
        String eventType,
        UUID userId,
        String userEmail,
        UUID productId,
        String sku,
        String productName,
        BigDecimal priceWhenAdded,
        BigDecimal previousPrice,
        BigDecimal newPrice,
        String currency
) {
}
