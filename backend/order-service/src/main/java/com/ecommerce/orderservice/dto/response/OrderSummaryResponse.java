package com.ecommerce.orderservice.dto.response;

import com.ecommerce.orderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of an order list. */
public record OrderSummaryResponse(
        String orderNumber,
        UUID userId,
        OrderStatus status,
        int itemCount,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        Instant placedAt
) {
}
