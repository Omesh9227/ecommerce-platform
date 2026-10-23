package com.ecommerce.cartservice.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One product in a cart, exactly as stored in Redis (JSON in the cart hash, field = productId).
 * sku / name / image / price are a snapshot from when the product was last added: they are only shown
 * when product-service can't be reached or the product is no longer for sale. Otherwise live data is used.
 *
 * priceWhenAdded is stored as a string so the Lua scripts (which decode and re-encode the JSON)
 * never turn it into a floating-point number.
 */
public record CartLine(
        UUID productId,
        String sku,
        String name,
        String imageUrl,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal priceWhenAdded,
        String currency,
        int quantity,
        Instant addedAt
) {
}
