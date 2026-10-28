package com.ecommerce.reviewservice.controller;

import com.ecommerce.reviewservice.dto.request.HideReviewRequest;
import com.ecommerce.reviewservice.dto.response.ApiResult;
import com.ecommerce.reviewservice.dto.response.PageResponse;
import com.ecommerce.reviewservice.dto.response.ReviewResponse;
import com.ecommerce.reviewservice.entity.ReviewStatus;
import com.ecommerce.reviewservice.service.ReviewCommands;
import com.ecommerce.reviewservice.service.ReviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Moderation (ADMIN): see all reviews, hide inappropriate ones, show them again. */
@RestController
@RequestMapping("/api/v1/reviews/admin")
@RequiredArgsConstructor
@Tag(name = "Reviews (admin)", description = "Moderation - ADMIN only")
public class AdminReviewController {

    private final ReviewCommands commands;
    private final ReviewQueryService queryService;

    @Operation(summary = "All reviews", description = "Filters: status (VISIBLE / HIDDEN), productId. Includes userId.")
    @GetMapping
    public ResponseEntity<ApiResult<PageResponse<ReviewResponse>>> all(
            @RequestParam(name = "status", required = false) ReviewStatus status,
            @RequestParam(name = "productId", required = false) UUID productId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Reviews fetched successfully", queryService.forAdmin(status, productId, pageable)));
    }

    @Operation(summary = "Hide a review", description = "No longer shown or counted in the product's rating; its author "
            + "sees the reason. Hiding again just updates the reason.")
    @PostMapping("/{reviewId}/hide")
    public ResponseEntity<ApiResult<ReviewResponse>> hide(@PathVariable("reviewId") UUID reviewId,
                                                          @Valid @RequestBody HideReviewRequest request) {
        return ResponseEntity.ok(ApiResult.of("Review hidden", ReviewResponse.adminView(commands.hide(reviewId, request.reason()))));
    }

    @Operation(summary = "Show a hidden review again")
    @PostMapping("/{reviewId}/unhide")
    public ResponseEntity<ApiResult<ReviewResponse>> unhide(@PathVariable("reviewId") UUID reviewId) {
        return ResponseEntity.ok(ApiResult.of("Review visible again", ReviewResponse.adminView(commands.unhide(reviewId))));
    }
}
