package com.ecommerce.recommendationservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** A product card, with why it is recommended. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendedProduct(
        UUID productId,
        @Schema(example = "Pixel Buds Pro") String name,
        @Schema(example = "Google") String brand,
        String imageUrl,
        @Schema(example = "19999.00") BigDecimal sellingPrice,
        @Schema(example = "INR") String currency,
        @Schema(example = "4.6") BigDecimal averageRating,
        int reviewCount,
        boolean inStock,
        UUID categoryId,
        @Schema(example = "Audio") String categoryName,
        @Schema(example = "Because you bought Pixel 9") String reason
) {

    public RecommendedProduct withReason(String newReason) {
        return new RecommendedProduct(productId, name, brand, imageUrl, sellingPrice, currency, averageRating, reviewCount,
                inStock, categoryId, categoryName, newReason);
    }
}
