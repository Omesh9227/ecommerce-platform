package com.ecommerce.wishlistservice.event;

import com.ecommerce.wishlistservice.entity.WishlistItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on wishlist-events (key = user id). notification-service turns WISHLIST_PRICE_DROPPED into an email.
 *
 * @param previousPrice the price the customer last saw (when saved, or in our previous price-drop email)
 * @param newPrice      the current selling price
 */
public record WishlistEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID userId,
        String userEmail,
        UUID productId,
        String sku,
        String productName,
        String imageUrl,
        BigDecimal priceWhenAdded,
        BigDecimal previousPrice,
        BigDecimal newPrice,
        String currency
) {

    public static WishlistEvent priceDropped(WishlistItem item, String productName, BigDecimal newPrice, String currency,
                                             Instant now) {
        return new WishlistEvent(UUID.randomUUID(), EventTypes.WISHLIST_PRICE_DROPPED, now, item.getUserId(),
                item.getUserEmail(), item.getProductId(), item.getSku(), productName, item.getImageUrl(),
                item.getPriceWhenAdded(), item.priceLastSeen(), newPrice, currency);
    }
}
