package com.ecommerce.recommendationservice.controller;

import com.ecommerce.recommendationservice.dto.response.ApiResult;
import com.ecommerce.recommendationservice.dto.response.ErrorResponse;
import com.ecommerce.recommendationservice.dto.response.RecommendationList;
import com.ecommerce.recommendationservice.recommend.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Recommendations anyone can see: on a product page, and what's trending. */
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendations (public)", description = "Product page rows and trending products; no login")
@SecurityRequirements
@ApiResponse(responseCode = "400", description = "limit out of range",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class ProductRecommendationController {

    private final RecommendationService service;

    @Operation(summary = "Frequently bought together", description = "Products most often in the same paid order.")
    @GetMapping("/products/{productId}/bought-together")
    public ResponseEntity<ApiResult<RecommendationList>> boughtTogether(
            @PathVariable("productId") UUID productId, @RequestParam(name = "limit", required = false) Integer limit) {
        return ok(service.boughtTogether(productId, limit));
    }

    @Operation(summary = "Customers who viewed this also viewed", description = "Products most often viewed by the same customers.")
    @GetMapping("/products/{productId}/also-viewed")
    public ResponseEntity<ApiResult<RecommendationList>> alsoViewed(
            @PathVariable("productId") UUID productId, @RequestParam(name = "limit", required = false) Integer limit) {
        return ok(service.alsoViewed(productId, limit));
    }

    @Operation(summary = "Similar products", description = "Same or related category; similar price and the same brand rank higher.")
    @ApiResponse(responseCode = "404", description = "Unknown product",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/products/{productId}/similar")
    public ResponseEntity<ApiResult<RecommendationList>> similar(
            @PathVariable("productId") UUID productId, @RequestParam(name = "limit", required = false) Integer limit) {
        return ok(service.similar(productId, limit));
    }

    @Operation(summary = "Trending", description = "Most units sold recently (app.recommendation.trending-days), "
            + "optionally in a category and its sub-categories; topped up with top-rated products.")
    @GetMapping("/trending")
    public ResponseEntity<ApiResult<RecommendationList>> trending(
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return ok(service.trending(categoryId, limit));
    }

    private static ResponseEntity<ApiResult<RecommendationList>> ok(RecommendationList list) {
        return ResponseEntity.ok(ApiResult.of(list.title(), list));
    }
}
