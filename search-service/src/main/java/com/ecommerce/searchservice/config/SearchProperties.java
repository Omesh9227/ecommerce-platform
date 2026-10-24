package com.ecommerce.searchservice.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;

/**
 * app.search.* (config-repo/search-service.properties).
 *
 * @param priceRanges      bounds of the price filter's buckets, ascending: 500,1000 = under 500 / 500-1000 / 1000 and above
 * @param maxPageSize      largest page a search may ask for
 * @param suggestionLimit  products in the search-box suggestions
 * @param popularDays      "popular searches" counts searches made in this many days
 * @param facetLimit       brands / categories listed in the filters
 */
@Validated
@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        @NotBlank String currency,
        @NotEmpty List<BigDecimal> priceRanges,
        @Positive @Max(100) int maxPageSize,
        @Positive @Max(20) int suggestionLimit,
        @Positive int popularDays,
        @Positive @Max(100) int facetLimit
) {

    public SearchProperties {
        if (priceRanges != null) {
            for (int i = 0; i < priceRanges.size(); i++) {
                if (priceRanges.get(i).signum() <= 0 || (i > 0 && priceRanges.get(i).compareTo(priceRanges.get(i - 1)) <= 0)) {
                    throw new IllegalArgumentException("app.search.price-ranges must be positive and ascending: " + priceRanges);
                }
            }
            priceRanges = List.copyOf(priceRanges);
        }
    }
}
