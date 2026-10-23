package com.ecommerce.reviewservice.controller;

import com.ecommerce.reviewservice.dto.request.ReviewRequest;
import com.ecommerce.reviewservice.dto.response.ApiResult;
import com.ecommerce.reviewservice.dto.response.ErrorResponse;
import com.ecommerce.reviewservice.dto.response.HelpfulVoteResponse;
import com.ecommerce.reviewservice.dto.response.PageResponse;
import com.ecommerce.reviewservice.dto.response.RatingSummaryResponse;
import com.ecommerce.reviewservice.dto.response.ReviewResponse;
import com.ecommerce.reviewservice.security.AuthenticatedUser;
import com.ecommerce.reviewservice.service.RatingService;
import com.ecommerce.reviewservice.service.ReviewCommands;
import com.ecommerce.reviewservice.service.ReviewQueryService;
import com.ecommerce.reviewservice.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Reading a product's reviews is public; writing, editing, deleting and voting need a logged-in customer. */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Product reviews and ratings")
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewCommands commands;
    private final ReviewQueryService queryService;
    private final RatingService ratingService;

    // ---------- Public ----------

    @Operation(summary = "A product's reviews (public)", description = "Visible reviews only, newest first by default. "
            + "Filters: rating (1-5), verifiedOnly. Sortable: createdAt, rating, helpfulCount "
            + "(e.g. sort=helpfulCount,desc for \"most helpful\").")
    @SecurityRequirements
    @ApiResponse(responseCode = "400", description = "Invalid rating filter or sort",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/products/{productId}")
    public ResponseEntity<ApiResult<PageResponse<ReviewResponse>>> productReviews(
            @PathVariable("productId") UUID productId,
            @RequestParam(name = "rating", required = false) Integer rating,
            @RequestParam(name = "verifiedOnly", defaultValue = "false") boolean verifiedOnly,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Reviews fetched successfully",
                queryService.forProduct(productId, rating, verifiedOnly, pageable)));
    }

    @Operation(summary = "A product's rating (public)", description = "Average (one decimal), number of reviews, and "
            + "how many reviews gave each number of stars. A product without reviews: 0.0 from 0.")
    @SecurityRequirements
    @GetMapping("/products/{productId}/summary")
    public ResponseEntity<ApiResult<RatingSummaryResponse>> summary(@PathVariable("productId") UUID productId) {
        return ResponseEntity.ok(ApiResult.of("Rating fetched successfully", ratingService.summary(productId)));
    }

    // ---------- Logged in ----------

    @Operation(summary = "Review a product", description = "One review per product - edit it to change your mind. "
            + "Marked verifiedPurchase if you received this product. Shown with your first name and initial.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Review published"),
            @ApiResponse(responseCode = "400", description = "Invalid rating, title or comment",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Only buyers may review (when that's switched on)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Product not found or not for sale",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "You already reviewed this product",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "product-service is not responding",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/products/{productId}")
    public ResponseEntity<ApiResult<ReviewResponse>> create(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody ReviewRequest request,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Review published", reviewService.create(productId, currentUser, authorization, request)));
    }

    @Operation(summary = "My reviews", description = "Including hidden ones, with the reason. Newest first by default.")
    @GetMapping("/me")
    public ResponseEntity<ApiResult<PageResponse<ReviewResponse>>> mine(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Reviews fetched successfully", queryService.mine(currentUser.id(), pageable)));
    }

    @Operation(summary = "Edit my review", description = "Rating, title and comment are replaced; the product's rating "
            + "is updated. Someone else's review: 404.")
    @PutMapping("/{reviewId}")
    public ResponseEntity<ApiResult<ReviewResponse>> edit(
            @PathVariable("reviewId") UUID reviewId,
            @Valid @RequestBody ReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Review updated", reviewService.edit(reviewId, currentUser, request)));
    }

    @Operation(summary = "Delete my review", description = "Admins can delete any review.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(
            @PathVariable("reviewId") UUID reviewId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        commands.delete(reviewId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark a review as helpful", description = "Once per customer (again changes nothing); "
            + "not on your own review.")
    @PostMapping("/{reviewId}/helpful")
    public ResponseEntity<ApiResult<HelpfulVoteResponse>> voteHelpful(
            @PathVariable("reviewId") UUID reviewId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Thanks for your feedback", commands.voteHelpful(reviewId, currentUser)));
    }

    @Operation(summary = "Take back my \"helpful\" vote")
    @DeleteMapping("/{reviewId}/helpful")
    public ResponseEntity<ApiResult<HelpfulVoteResponse>> removeHelpfulVote(
            @PathVariable("reviewId") UUID reviewId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Vote removed", commands.removeHelpfulVote(reviewId, currentUser)));
    }
}
