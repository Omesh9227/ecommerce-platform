package com.ecommerce.wishlistservice.controller;

import com.ecommerce.wishlistservice.dto.request.AddToWishlistRequest;
import com.ecommerce.wishlistservice.dto.request.MoveToCartRequest;
import com.ecommerce.wishlistservice.dto.response.ApiResult;
import com.ecommerce.wishlistservice.dto.response.ErrorResponse;
import com.ecommerce.wishlistservice.dto.response.WishlistContainsResponse;
import com.ecommerce.wishlistservice.dto.response.WishlistItemResponse;
import com.ecommerce.wishlistservice.dto.response.WishlistResponse;
import com.ecommerce.wishlistservice.security.AuthenticatedUser;
import com.ecommerce.wishlistservice.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The caller's own wishlist (from the JWT - there is no user id in any URL). */
@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
@Tag(name = "Wishlist", description = "Save products for later, see their live prices, move them to the cart")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Not logged in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class WishlistController {

    private final WishlistService wishlistService;

    @Operation(summary = "My wishlist", description = "Newest first, with live prices and stock. priceDropped / "
            + "priceDifference compare with the price when each product was saved. If product-service is down the "
            + "wishlist is still returned, from the saved snapshot (pricesUpToDate=false).")
    @GetMapping
    public ResponseEntity<ApiResult<WishlistResponse>> getWishlist(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Wishlist fetched successfully", wishlistService.getWishlist(currentUser.id())));
    }

    @Operation(summary = "Save a product", description = "Any product that is for sale, also when it's out of stock. "
            + "Saving it again changes nothing (200).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Saved"),
            @ApiResponse(responseCode = "200", description = "Was already saved"),
            @ApiResponse(responseCode = "400", description = "Invalid request, or the wishlist is full",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Product not found or not for sale",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "product-service is not responding",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/items")
    public ResponseEntity<ApiResult<WishlistItemResponse>> add(
            @Valid @RequestBody AddToWishlistRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        WishlistService.AddOutcome outcome = wishlistService.add(currentUser, request.productId());
        return outcome.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.of("Saved to your wishlist", outcome.item()))
                : ResponseEntity.ok(ApiResult.of("Already in your wishlist", outcome.item()));
    }

    @Operation(summary = "Remove a product", description = "Removing one that isn't saved is fine too.")
    @ApiResponse(responseCode = "204", description = "Removed")
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> remove(
            @PathVariable("productId") UUID productId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        wishlistService.remove(currentUser.id(), productId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Empty my wishlist")
    @ApiResponse(responseCode = "204", description = "Emptied")
    @DeleteMapping
    public ResponseEntity<Void> clear(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        wishlistService.clear(currentUser.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Which of these products have I saved?",
            description = "For product lists and pages (the filled heart). Up to 100 ids, comma-separated.")
    @ApiResponse(responseCode = "400", description = "More than 100 ids, or an invalid id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/contains")
    public ResponseEntity<ApiResult<WishlistContainsResponse>> contains(
            @Parameter(description = "Comma-separated product ids") @RequestParam(name = "productIds") List<UUID> productIds,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Checked", wishlistService.contains(currentUser.id(), productIds)));
    }

    @Operation(summary = "Move a saved product to my cart", description = "Adds it to the cart (the cart's rules "
            + "apply: stock, at most 10 per product...) and then removes it from the wishlist. If the cart refuses, the "
            + "cart's answer is returned and the product stays saved. Body optional: {\"quantity\": 1}.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Moved; the updated wishlist is returned"),
            @ApiResponse(responseCode = "404", description = "Not in your wishlist, or no longer for sale",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The cart refused, e.g. out of stock",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "cart-service is not responding",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/items/{productId}/move-to-cart")
    public ResponseEntity<ApiResult<WishlistResponse>> moveToCart(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody(required = false) MoveToCartRequest request,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        int quantity = request == null ? 1 : request.quantityOrDefault();
        WishlistResponse wishlist = wishlistService.moveToCart(currentUser, authorization, productId, quantity);
        return ResponseEntity.ok(ApiResult.of("Moved to your cart", wishlist));
    }
}
