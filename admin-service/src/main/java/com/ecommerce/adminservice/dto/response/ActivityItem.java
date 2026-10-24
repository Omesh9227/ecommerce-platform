package com.ecommerce.adminservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** One line of the activity feed. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivityItem(
        Instant occurredAt,
        @Schema(example = "PAYMENT_FAILED") String kind,
        @Schema(example = "WARNING") String severity,
        @Schema(example = "Payment of INR 1,800.00 for ORD-3FA85F645717 failed: Your card was declined.") String message,
        String orderNumber,
        UUID productId
) {
}
