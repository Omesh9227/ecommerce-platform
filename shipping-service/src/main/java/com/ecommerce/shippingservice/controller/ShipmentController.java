package com.ecommerce.shippingservice.controller;

import com.ecommerce.shippingservice.dto.response.ApiResult;
import com.ecommerce.shippingservice.dto.response.DeliveryEstimateResponse;
import com.ecommerce.shippingservice.dto.response.ErrorResponse;
import com.ecommerce.shippingservice.dto.response.PageResponse;
import com.ecommerce.shippingservice.dto.response.ShipmentResponse;
import com.ecommerce.shippingservice.dto.response.ShipmentSummaryResponse;
import com.ecommerce.shippingservice.dto.response.TrackingResponse;
import com.ecommerce.shippingservice.security.AuthenticatedUser;
import com.ecommerce.shippingservice.service.ShipmentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Customers' shipments, public parcel tracking and delivery estimates. */
@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipments", description = "Track your orders; public tracking by tracking number; delivery estimates")
public class ShipmentController {

    private final ShipmentQueryService queryService;

    @Operation(summary = "My shipments", description = "Newest first. Sortable: createdAt, updatedAt, estimatedDeliveryDate, status.")
    @GetMapping
    public ResponseEntity<ApiResult<PageResponse<ShipmentSummaryResponse>>> myShipments(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(ApiResult.of("Shipments fetched successfully", queryService.mine(caller, pageable)));
    }

    @Operation(summary = "Shipment of one of my orders",
            description = "Status, carrier, tracking number, estimated delivery, address, items and the tracking history.")
    @ApiResponse(responseCode = "404", description = "Not your order, or not paid yet (no shipment)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/orders/{orderNumber}")
    public ResponseEntity<ApiResult<ShipmentResponse>> forOrder(
            @PathVariable("orderNumber") String orderNumber,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(ApiResult.of("Shipment fetched successfully", queryService.forOrder(orderNumber, caller)));
    }

    @Operation(summary = "Track a parcel (public)",
            description = "By tracking number, no login: status, estimated delivery, destination city and the tracking history. "
                    + "No names, phone numbers or street addresses.")
    @SecurityRequirements
    @ApiResponse(responseCode = "404", description = "Unknown tracking number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<ApiResult<TrackingResponse>> track(@PathVariable("trackingNumber") String trackingNumber) {
        return ResponseEntity.ok(ApiResult.of("Tracking fetched successfully", queryService.track(trackingNumber)));
    }

    @Operation(summary = "Delivery estimate for a PIN code (public)",
            description = "When an order placed today would arrive, e.g. \"Delivery by Thu, 29 Oct\".")
    @SecurityRequirements
    @ApiResponse(responseCode = "400", description = "Not a 6-digit PIN code",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/delivery-estimate")
    public ResponseEntity<ApiResult<DeliveryEstimateResponse>> deliveryEstimate(
            @Parameter(example = "110001") @RequestParam("postalCode") String postalCode) {
        return ResponseEntity.ok(ApiResult.of("Delivery estimate", queryService.estimate(postalCode)));
    }
}
