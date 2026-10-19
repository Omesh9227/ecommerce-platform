package com.ecommerce.productservice.dto.response;

import com.ecommerce.productservice.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        String brand,
        CategorySummary category,
        BigDecimal price,
        BigDecimal discountPercent,
        BigDecimal sellingPrice,
        String currency,
        ProductStatus status,
        List<String> imageUrls,
        Instant createdAt,
        Instant updatedAt
) {
}
