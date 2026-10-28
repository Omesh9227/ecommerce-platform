package com.ecommerce.productservice.dto.request;

import com.ecommerce.productservice.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Query-string filters for GET /api/v1/products. All optional. */
public record ProductSearchCriteria(
        String search,
        UUID categoryId,
        String brand,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        ProductStatus status,
        Boolean inStock
) {
}
