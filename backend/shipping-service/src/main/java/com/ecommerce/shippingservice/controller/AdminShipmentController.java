package com.ecommerce.shippingservice.controller;

import com.ecommerce.shippingservice.dto.request.ShipRequest;
import com.ecommerce.shippingservice.dto.request.TrackingUpdateRequest;
import com.ecommerce.shippingservice.dto.response.ApiResult;
import com.ecommerce.shippingservice.dto.response.ErrorResponse;
import com.ecommerce.shippingservice.dto.response.PageResponse;
import com.ecommerce.shippingservice.dto.response.ShipmentResponse;
import com.ecommerce.shippingservice.dto.response.ShipmentSummaryResponse;
import com.ecommerce.shippingservice.entity.ShipmentStatus;
import com.ecommerce.shippingservice.security.AuthenticatedUser;
import com.ecommerce.shippingservice.service.ShipmentCommands;
import com.ecommerce.shippingservice.service.ShipmentQueryService;
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

/** Warehouse and carrier updates (ADMIN role, checked by SecurityConfig and by the gateway). */
@RestController
@RequestMapping("/api/v1/shipments/admin")
@RequiredArgsConstructor
@Tag(name = "Shipments (admin)", description = "Pack, ship and record carrier scans (ADMIN)")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Not logged in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Not an ADMIN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No such shipment",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Not allowed from the shipment's current status",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class AdminShipmentController {

    private final ShipmentQueryService queryService;
    private final ShipmentCommands commands;

    @Operation(summary = "All shipments", description = "Filter by status (e.g. PENDING = to pack), orderNumber, carrier. "
            + "Oldest first by default, so the warehouse works through them in order.")
    @GetMapping
    public ResponseEntity<ApiResult<PageResponse<ShipmentSummaryResponse>>> all(
            @RequestParam(name = "status", required = false) ShipmentStatus status,
            @RequestParam(name = "orderNumber", required = false) String orderNumber,
            @RequestParam(name = "carrier", required = false) String carrier,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Shipments fetched successfully",
                queryService.all(status, orderNumber, carrier, pageable)));
    }

    @Operation(summary = "One shipment", description = "With address, items, tracking history and the customer's userId.")
    @GetMapping("/{shipmentId}")
    public ResponseEntity<ApiResult<ShipmentResponse>> one(@PathVariable("shipmentId") UUID shipmentId) {
        return ResponseEntity.ok(ApiResult.of("Shipment fetched successfully", queryService.byId(shipmentId)));
    }

    @Operation(summary = "Packed", description = "PENDING -> PACKED.")
    @PostMapping("/{shipmentId}/pack")
    public ResponseEntity<ApiResult<ShipmentResponse>> pack(
            @PathVariable("shipmentId") UUID shipmentId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
        return ResponseEntity.ok(ApiResult.of("Shipment packed", commands.pack(shipmentId, admin.id())));
    }

    @Operation(summary = "Shipped", description = "PACKED -> SHIPPED with a carrier (and its tracking number, or we generate "
            + "one). The order becomes SHIPPED and the customer gets an email.")
    @ApiResponse(responseCode = "400", description = "Unknown carrier or invalid tracking number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{shipmentId}/ship")
    public ResponseEntity<ApiResult<ShipmentResponse>> ship(
            @PathVariable("shipmentId") UUID shipmentId,
            @Valid @RequestBody ShipRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
        return ResponseEntity.ok(ApiResult.of("Shipment shipped", commands.ship(shipmentId, request, admin.id())));
    }

    @Operation(summary = "Carrier scan", description = "IN_TRANSIT (with the hub as location), OUT_FOR_DELIVERY, "
            + "DELIVERY_FAILED (with the reason as note) or DELIVERED. DELIVERED completes the order.")
    @ApiResponse(responseCode = "400", description = "Invalid status, or location/note missing",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{shipmentId}/tracking")
    public ResponseEntity<ApiResult<ShipmentResponse>> tracking(
            @PathVariable("shipmentId") UUID shipmentId,
            @Valid @RequestBody TrackingUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
        return ResponseEntity.ok(ApiResult.of("Tracking updated", commands.update(shipmentId, request, admin.id())));
    }
}
