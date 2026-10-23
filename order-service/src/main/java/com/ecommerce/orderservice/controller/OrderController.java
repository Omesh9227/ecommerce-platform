package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.dto.request.CancelOrderRequest;
import com.ecommerce.orderservice.dto.request.CheckoutRequest;
import com.ecommerce.orderservice.dto.response.ApiResult;
import com.ecommerce.orderservice.dto.response.ErrorResponse;
import com.ecommerce.orderservice.dto.response.OrderResponse;
import com.ecommerce.orderservice.dto.response.OrderSummaryResponse;
import com.ecommerce.orderservice.dto.response.PageResponse;
import com.ecommerce.orderservice.entity.OrderStatus;
import com.ecommerce.orderservice.security.AuthenticatedUser;
import com.ecommerce.orderservice.service.CheckoutService;
import com.ecommerce.orderservice.service.CheckoutService.CheckoutResult;
import com.ecommerce.orderservice.service.OrderLifecycleService;
import com.ecommerce.orderservice.service.OrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own orders. */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Checkout and the logged-in user's orders")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Not logged in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class OrderController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final CheckoutService checkoutService;
    private final OrderQueryService queryService;
    private final OrderLifecycleService lifecycleService;

    @Operation(summary = "Place an order for everything in my cart",
            description = "Send a new random Idempotency-Key (e.g. a UUID) for each order. Sending the same key again "
                    + "(e.g. after a timeout) returns the same order with 200 instead of placing a second one. "
                    + "Stock is reserved; the order waits for payment (PENDING_PAYMENT) and the cart is emptied.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order placed"),
            @ApiResponse(responseCode = "200", description = "Same Idempotency-Key as an earlier order: that order"),
            @ApiResponse(responseCode = "400", description = "Missing/invalid Idempotency-Key, invalid address or empty cart",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Not enough stock, item no longer sold, or total changed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Another service is down - retry with the same key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/checkout")
    public ResponseEntity<ApiResult<OrderResponse>> checkout(
            @Parameter(description = "A unique value per order, e.g. a UUID", required = true)
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        CheckoutResult result = checkoutService.checkout(currentUser, authorization, idempotencyKey, request);
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.of("Order placed successfully", result.order()))
                : ResponseEntity.ok(ApiResult.of("Order already placed with this Idempotency-Key", result.order()));
    }

    @Operation(summary = "My orders", description = "Newest first. Sortable: createdAt, totalAmount, status.")
    @GetMapping
    public ResponseEntity<ApiResult<PageResponse<OrderSummaryResponse>>> myOrders(
            @RequestParam(name = "status", required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Orders fetched successfully",
                queryService.myOrders(currentUser.id(), status, pageable)));
    }

    @Operation(summary = "One of my orders, with its timeline", description = "Admins can open any order.")
    @ApiResponse(responseCode = "404", description = "No such order (or not yours)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{orderNumber}")
    public ResponseEntity<ApiResult<OrderResponse>> getOrder(
            @PathVariable("orderNumber") String orderNumber,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Order fetched successfully", queryService.getOrder(orderNumber, currentUser)));
    }

    @Operation(summary = "Cancel my order", description = "Possible while it waits for payment; the reserved stock is "
            + "released. Cancelling twice is harmless.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelled"),
            @ApiResponse(responseCode = "404", description = "No such order (or not yours)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Already paid, shipped or delivered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{orderNumber}/cancel")
    public ResponseEntity<ApiResult<OrderResponse>> cancel(
            @PathVariable("orderNumber") String orderNumber,
            @Valid @RequestBody(required = false) CancelOrderRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Order cancelled",
                lifecycleService.cancel(orderNumber, currentUser, request == null ? null : request.reason())));
    }
}
