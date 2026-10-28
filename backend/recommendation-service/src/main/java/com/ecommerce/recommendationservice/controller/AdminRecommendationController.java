package com.ecommerce.recommendationservice.controller;

import com.ecommerce.recommendationservice.dto.response.ApiResult;
import com.ecommerce.recommendationservice.dto.response.RecommendationStatsResponse;
import com.ecommerce.recommendationservice.recommend.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN: what recommendations are built from. */
@RestController
@RequestMapping("/api/v1/recommendations/admin")
@RequiredArgsConstructor
@Tag(name = "Recommendations (admin)", description = "Statistics (ADMIN)")
public class AdminRecommendationController {

    private final RecommendationService service;

    @Operation(summary = "Statistics", description = "Products known / recommendable, paid orders counted, pairs bought and viewed together.")
    @GetMapping("/stats")
    public ResponseEntity<ApiResult<RecommendationStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResult.of("Recommendation statistics", service.stats()));
    }
}
