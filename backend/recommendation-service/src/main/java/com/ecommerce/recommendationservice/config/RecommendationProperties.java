package com.ecommerce.recommendationservice.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * app.recommendation.* (config-repo/recommendation-service.properties).
 *
 * @param defaultLimit  products per list when the request doesn't say
 * @param maxLimit      most products a list may ask for
 * @param trendingDays  "trending" counts units sold in this many days
 * @param viewHistory   a customer's most recent views used for "recently viewed", "also viewed" and "for you"
 */
@Validated
@ConfigurationProperties(prefix = "app.recommendation")
public record RecommendationProperties(
        @NotBlank String currency,
        @Positive int defaultLimit,
        @Positive @Max(50) int maxLimit,
        @Positive @Max(365) int trendingDays,
        @Positive @Max(100) int viewHistory
) {

    public RecommendationProperties {
        if (defaultLimit > maxLimit) {
            throw new IllegalArgumentException("app.recommendation.default-limit can't be more than max-limit");
        }
    }
}
