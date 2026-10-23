package com.ecommerce.inventoryservice.controller;

import com.ecommerce.inventoryservice.dto.request.ReservationRequest;
import com.ecommerce.inventoryservice.dto.response.ApiResult;
import com.ecommerce.inventoryservice.dto.response.ErrorResponse;
import com.ecommerce.inventoryservice.dto.response.ReservationResponse;
import com.ecommerce.inventoryservice.security.AuthenticatedUser;
import com.ecommerce.inventoryservice.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stock reservations for orders. Called manually by admins for now; order-service will call these in a later phase.
 */
@RestController
@RequestMapping("/api/v1/inventory/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Hold, release and confirm stock for orders (ADMIN)")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Not logged in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Not an ADMIN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "Reserve stock for an order",
            description = "All items or none. Repeating the same orderReference returns the existing reservation with 200.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Stock reserved"),
            @ApiResponse(responseCode = "200", description = "Reservation already existed (safe retry)"),
            @ApiResponse(responseCode = "400", description = "Validation failed or unknown product",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Not enough stock or product not for sale",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResult<ReservationResponse>> reserve(
            @Valid @RequestBody ReservationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ReservationService.ReservationResult result = reservationService.reserve(request, currentUser.id());
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.of("Stock reserved successfully", result.reservation()))
                : ResponseEntity.ok(ApiResult.of("Reservation already exists", result.reservation()));
    }

    @Operation(summary = "Get a reservation by order reference")
    @ApiResponse(responseCode = "404", description = "Reservation not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{orderReference}")
    public ResponseEntity<ApiResult<ReservationResponse>> get(@PathVariable("orderReference") String orderReference) {
        return ResponseEntity.ok(ApiResult.of("Reservation fetched successfully",
                reservationService.getReservation(orderReference)));
    }

    @Operation(summary = "Release a reservation", description = "Held stock becomes available again. Releasing twice is harmless.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Released"),
            @ApiResponse(responseCode = "404", description = "Reservation not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Already confirmed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{orderReference}/release")
    public ResponseEntity<ApiResult<ReservationResponse>> release(
            @PathVariable("orderReference") String orderReference,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Reservation released successfully",
                reservationService.release(orderReference, currentUser.id())));
    }

    @Operation(summary = "Confirm a reservation", description = "The sale happened: held stock is removed from on-hand. Confirming twice is harmless.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Confirmed"),
            @ApiResponse(responseCode = "404", description = "Reservation not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Already released",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{orderReference}/confirm")
    public ResponseEntity<ApiResult<ReservationResponse>> confirm(
            @PathVariable("orderReference") String orderReference,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Reservation confirmed successfully",
                reservationService.confirm(orderReference, currentUser.id())));
    }
}
