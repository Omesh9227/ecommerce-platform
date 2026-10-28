package com.ecommerce.searchservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The filters to show next to the results, with how many results each would give. Each one is counted with all the
 * other filters applied but not its own (picking "Google" still shows how many "Samsung" there are).
 */
public record Facets(
        List<Value> brands,
        List<CategoryCount> categories,
        List<PriceRange> priceRanges,
        List<Rating> ratings,
        Availability availability
) {

    public record Value(@Schema(example = "Google") String value, long count) {
    }

    public record CategoryCount(UUID id, @Schema(example = "Phones") String name, long count) {
    }

    /** @param max exclusive; absent for the last range */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PriceRange(@Schema(example = "INR 1,000 - INR 5,000") String label, BigDecimal min, BigDecimal max, long count) {
    }

    public record Rating(@Schema(example = "4") int minRating, @Schema(example = "4 stars & up") String label, long count) {
    }

    public record Availability(long inStock, long outOfStock) {
    }
}
