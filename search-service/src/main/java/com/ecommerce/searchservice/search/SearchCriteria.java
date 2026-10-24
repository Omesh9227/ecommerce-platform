package com.ecommerce.searchservice.search;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A search request after validation.
 *
 * @param brands     any of these (case-insensitive); empty = all brands
 * @param categoryId this category and all its sub-categories
 * @param inStock    true = only available, false = only sold out, null = both
 */
public record SearchCriteria(
        String query,
        UUID categoryId,
        List<String> brands,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer minRating,
        Boolean inStock,
        SortOrder sort,
        int page,
        int size
) {
}
