package com.ecommerce.adminservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The fields of payment-service's payment-events that reports need. */
public record PaymentEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        BigDecimal amount,
        String currency,
        String method,
        String reason
) {
}
