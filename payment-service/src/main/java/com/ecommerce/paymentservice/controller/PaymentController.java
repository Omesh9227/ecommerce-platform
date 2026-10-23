package com.ecommerce.paymentservice.controller;

import com.ecommerce.paymentservice.dto.request.PayRequest;
import com.ecommerce.paymentservice.dto.response.ApiResult;
import com.ecommerce.paymentservice.dto.response.ErrorResponse;
import com.ecommerce.paymentservice.dto.response.PaymentResponse;
import com.ecommerce.paymentservice.security.AuthenticatedUser;
import com.ecommerce.paymentservice.service.PayResult;
import com.ecommerce.paymentservice.service.PaymentQueryService;
import com.ecommerce.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Paying the caller's own orders. */
@RestController
@RequestMapping("/api/v1/payments/orders")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Pay for your orders and see payment status")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Not logged in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No payment for this order (or not yours)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentQueryService queryService;

    @Operation(summary = "Pay for an order", description = "The amount is always the order's total. Send a new "
            + "Idempotency-Key per attempt; repeating a key returns that attempt's result without charging again. "
            + "Test cards: 4242 4242 4242 4242 succeeds, 4000 0000 0000 0002 is declined, 4000 0000 0000 9995 has "
            + "insufficient funds. UPI ids starting with 'fail' are declined.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paid; the order is confirmed shortly after (via Kafka)"),
            @ApiResponse(responseCode = "400", description = "Missing Idempotency-Key, invalid or expired card, missing UPI id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "402", description = "Declined - nothing was charged; try again with another method",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Already paid, or the order was cancelled",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Payment provider unavailable - nothing was charged",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{orderNumber}/pay")
    public ResponseEntity<?> pay(
            @PathVariable("orderNumber") String orderNumber,
            @Parameter(description = "A unique value per payment attempt, e.g. a UUID", required = true)
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PayRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpRequest) {
        PayResult result = paymentService.pay(orderNumber, currentUser, idempotencyKey, request);
        if (result.succeeded()) {
            return ResponseEntity.ok(ApiResult.of("Payment successful", result.payment()));
        }
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(ErrorResponse.of(HttpStatus.PAYMENT_REQUIRED, result.failureMessage(), httpRequest.getRequestURI()));
    }

    @Operation(summary = "Payment of one of my orders", description = "Status, how it was paid, and every attempt. Admins can see any.")
    @GetMapping("/{orderNumber}")
    public ResponseEntity<ApiResult<PaymentResponse>> forOrder(
            @PathVariable("orderNumber") String orderNumber,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Payment fetched successfully", queryService.forOrder(orderNumber, currentUser)));
    }
}
