package com.ecommerce.searchservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** One product in the results: enough for a result card (open it with product-service for the full page). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchHit(
        UUID productId,
        @Schema(example = "PIXEL-9") String sku,
        @Schema(example = "Pixel 9") String name,
        @Schema(example = "Google") String brand,
        String imageUrl,
        @Schema(example = "79999.00") BigDecimal price,
        @Schema(example = "71999.10") BigDecimal sellingPrice,
        @Schema(example = "10.00") BigDecimal discountPercent,
        @Schema(example = "INR") String currency,
        @Schema(example = "4.5") BigDecimal averageRating,
        @Schema(example = "120") int reviewCount,
        boolean inStock,
        @Schema(example = "3", description = "Units available now (\"Only 3 left\")") int availableQuantity,
        UUID categoryId,
        @Schema(example = "Phones") String categoryName
) {
}
