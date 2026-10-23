package com.ecommerce.paymentservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/** The fields of order-service's events that payment-service needs (other fields are ignored). */
public record OrderEvent(
        UUID eventId,
        String eventType,
        UUID orderId,
        String orderNumber,
        UUID userId,
        BigDecimal totalAmount,
        String currency,
        String reason,
        long orderVersion
) {
}
