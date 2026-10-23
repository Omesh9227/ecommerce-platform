package com.ecommerce.wishlistservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The fields of product-service's product-events that wishlists need (others are ignored).
 * price / sellingPrice are null in events written before product-service started sending them.
 */
public record ProductEvent(
        UUID eventId,
        String eventType,
        UUID productId,
        String sku,
        String name,
        String status,
        BigDecimal price,
        BigDecimal sellingPrice,
        long productVersion
) {

    public static final String STATUS_ACTIVE = "ACTIVE";

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}
