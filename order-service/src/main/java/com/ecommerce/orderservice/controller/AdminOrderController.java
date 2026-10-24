package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.dto.request.CancelOrderRequest;
import com.ecommerce.orderservice.dto.response.ApiResult;
import com.ecommerce.orderservice.dto.response.ErrorResponse;
import com.ecommerce.orderservice.dto.response.OrderResponse;
import com.ecommerce.orderservice.dto.response.OrderSummaryResponse;
import com.ecommerce.orderservice.dto.response.PageResponse;
import com.ecommerce.orderservice.entity.OrderStatus;
import com.ecommerce.orderservice.security.AuthenticatedUser;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Order management for admins (ADMIN role, checked by SecurityConfig and by the gateway).
 * Shipping and delivery are done in shipping-service (/api/v1/shipments/admin); orders follow its events.
 */
@RestController
@RequestMapping("/api/v1/orders/admin")
@RequiredArgsConstructor
@Tag(name = "Orders (admin)", description = "All orders and their lifecycle (ADMIN)")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Not logged in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Not an ADMIN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No such order",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Not allowed from the order's current status",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class AdminOrderController {

    private final OrderQueryService queryService;
    private final OrderLifecycleService lifecycleService;

    @Operation(summary = "All orders", description = "Filter by status and/or userId. Includes internal statuses "
            + "(CREATED, CHECKOUT_FAILED). Sortable: createdAt, totalAmount, status.")
    @GetMapping
    public ResponseEntity<ApiResult<PageResponse<OrderSummaryResponse>>> allOrders(
            @RequestParam(name = "status", required = false) OrderStatus status,
            @RequestParam(name = "userId", required = false) UUID userId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Orders fetched successfully", queryService.allOrders(status, userId, pageable)));
    }

    @Operation(summary = "Cancel any order", description = "Same rules as the customer's cancel: before shipping; paid orders are refunded.")
    @PostMapping("/{orderNumber}/cancel")
    public ResponseEntity<ApiResult<OrderResponse>> cancel(
            @PathVariable("orderNumber") String orderNumber,
            @Valid @RequestBody(required = false) CancelOrderRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
        return ResponseEntity.ok(ApiResult.of("Order cancelled",
                lifecycleService.cancel(orderNumber, admin, request == null ? null : request.reason())));
    }
}
