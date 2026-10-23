package com.ecommerce.reviewservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.review.* (from config-repo/review-service.properties at runtime).
 *
 * @param verifiedPurchaseRequired true: only customers who received the product (a delivered order) may review it.
 *                                 false: anyone logged in may; reviews from buyers are marked "verified purchase".
 */
@ConfigurationProperties(prefix = "app.review")
public record ReviewProperties(boolean verifiedPurchaseRequired) {
}
