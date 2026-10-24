package com.ecommerce.searchservice.search;

import com.ecommerce.searchservice.exception.BadRequestException;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** How results are ordered (?sort=...). */
public enum SortOrder {
    /** Best match first (in-stock first); without a query: best rated, then most reviewed, then newest. */
    RELEVANCE,
    PRICE_ASC,
    PRICE_DESC,
    /** Highest average rating, then most reviews. */
    RATING,
    NEWEST;

    public static SortOrder parse(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid sort '" + value + "'. Use one of: "
                    + Arrays.stream(values()).map(s -> s.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", ")));
        }
    }
}
