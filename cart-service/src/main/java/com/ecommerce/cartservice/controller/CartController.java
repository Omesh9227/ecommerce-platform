package com.ecommerce.cartservice.controller;

import com.ecommerce.cartservice.dto.request.AddItemRequest;
import com.ecommerce.cartservice.dto.request.UpdateQuantityRequest;
import com.ecommerce.cartservice.dto.response.ApiResult;
import com.ecommerce.cartservice.dto.response.CartResponse;
import com.ecommerce.cartservice.dto.response.ErrorResponse;
import com.ecommerce.cartservice.security.AuthenticatedUser;
import com.ecommerce.cartservice.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The caller's own cart. The user comes from the JWT, never from the URL. */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "The logged-in user's shopping cart")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Not logged in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Redis or product-service unavailable",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Get my cart", description = "Live prices and stock. If product-service is down the cart is "
            + "still returned, with pricesUpToDate=false and status UNKNOWN on each item.")
    @GetMapping
    public ResponseEntity<ApiResult<CartResponse>> getCart(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Cart fetched successfully", cartService.getCart(currentUser.id())));
    }

    @Operation(summary = "Add a product", description = "Adds to the quantity if the product is already in the cart.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Added; the whole cart is returned"),
            @ApiResponse(responseCode = "400", description = "Validation failed, per-product limit or cart full",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Product not found or not for sale",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Out of stock or not enough stock",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/items")
    public ResponseEntity<ApiResult<CartResponse>> addItem(
            @Valid @RequestBody AddItemRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Item added to cart", cartService.addItem(currentUser.id(), request)));
    }

    @Operation(summary = "Change a product's quantity")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated; the whole cart is returned"),
            @ApiResponse(responseCode = "400", description = "Validation failed or over the per-product limit",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Product is not in the cart",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Not enough stock, or product no longer for sale",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/items/{productId}")
    public ResponseEntity<ApiResult<CartResponse>> updateQuantity(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody UpdateQuantityRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Quantity updated",
                cartService.updateQuantity(currentUser.id(), productId, request)));
    }

    @Operation(summary = "Remove a product", description = "Removing a product that isn't in the cart is not an error.")
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResult<CartResponse>> removeItem(
            @PathVariable("productId") UUID productId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Item removed from cart", cartService.removeItem(currentUser.id(), productId)));
    }

    @Operation(summary = "Empty my cart")
    @ApiResponse(responseCode = "204", description = "Cart emptied")
    @DeleteMapping
    public ResponseEntity<Void> clear(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        cartService.clear(currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
