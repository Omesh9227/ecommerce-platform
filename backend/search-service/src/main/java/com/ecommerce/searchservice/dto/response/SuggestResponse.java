package com.ecommerce.searchservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** What the search box offers while typing. */
public record SuggestResponse(
        @Schema(example = "pix") String query,
        List<Product> products,
        @Schema(example = "[\"Google\"]") List<String> brands,
        List<Category> categories,
        @Schema(description = "Popular searches starting with what was typed", example = "[\"pixel 9\", \"pixel buds\"]")
        List<String> searches
) {

    public record Product(UUID productId, String name, String brand, String imageUrl, BigDecimal sellingPrice) {
    }

    public record Category(UUID id, String name) {
    }
}
