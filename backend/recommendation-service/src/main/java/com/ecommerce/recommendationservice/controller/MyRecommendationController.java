package com.ecommerce.recommendationservice.controller;

import com.ecommerce.recommendationservice.dto.request.ViewRequest;
import com.ecommerce.recommendationservice.dto.response.ApiResult;
import com.ecommerce.recommendationservice.dto.response.ErrorResponse;
import com.ecommerce.recommendationservice.dto.response.RecommendationList;
import com.ecommerce.recommendationservice.recommend.RecommendationService;
import com.ecommerce.recommendationservice.security.AuthenticatedUser;
import com.ecommerce.recommendationservice.views.ViewHistory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in customer's own recommendations and viewing history (who it is comes from the JWT). */
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendations (personal)", description = "Recommended for you, recently viewed (logged-in customer)")
public class MyRecommendationController {

    private final RecommendationService service;
    private final ViewHistory viewHistory;

    @Operation(summary = "Recommended for you", description = "From your latest purchases and views; each product says why. "
            + "Leaves out what you already bought or recently viewed. New customers get bestsellers and top-rated products.")
    @GetMapping("/for-you")
    public ResponseEntity<ApiResult<RecommendationList>> forYou(
            @RequestParam(name = "limit", required = false) Integer limit,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        RecommendationList list = service.forYou(user.id(), limit);
        return ResponseEntity.ok(ApiResult.of(list.title(), list));
    }

    @Operation(summary = "Record a product view", description = "Call when the customer opens a product page. "
            + "Feeds recently viewed, \"also viewed\" and recommendations for you.")
    @ApiResponse(responseCode = "204", description = "Recorded")
    @ApiResponse(responseCode = "404", description = "Unknown product",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/views")
    public ResponseEntity<Void> view(@Valid @RequestBody ViewRequest request,
                                     @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        viewHistory.record(user.id(), request.productId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Recently viewed", description = "Most recent first.")
    @GetMapping("/recently-viewed")
    public ResponseEntity<ApiResult<RecommendationList>> recentlyViewed(
            @RequestParam(name = "limit", required = false) Integer limit,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        RecommendationList list = service.recentlyViewed(user.id(), limit);
        return ResponseEntity.ok(ApiResult.of(list.title(), list));
    }

    @Operation(summary = "Clear my viewing history")
    @ApiResponse(responseCode = "204", description = "Cleared")
    @DeleteMapping("/recently-viewed")
    public ResponseEntity<Void> clear(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        viewHistory.clear(user.id());
        return ResponseEntity.noContent().build();
    }
}
