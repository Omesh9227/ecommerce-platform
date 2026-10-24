package com.ecommerce.recommendationservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** A titled row of products, ready to show ("Frequently bought together"). */
public record RecommendationList(@Schema(example = "Frequently bought together") String title, List<RecommendedProduct> products) {
}
