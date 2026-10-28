package com.ecommerce.wishlistservice.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** The fields of product-service's ProductResponse that wishlists need (other fields are ignored). */
public record CatalogProduct(
        UUID id,
        String sku,
        String name,
        BigDecimal price,
        BigDecimal sellingPrice,
        String currency,
        String status,
        int availableQuantity,
        boolean inStock,
        List<String> imageUrls
) {

    public String firstImageUrl() {
        return imageUrls == null || imageUrls.isEmpty() ? null : imageUrls.get(0);
    }
}
