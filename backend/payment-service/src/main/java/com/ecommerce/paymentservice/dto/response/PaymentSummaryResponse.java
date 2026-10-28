package com.ecommerce.paymentservice.dto.response;

import com.ecommerce.paymentservice.entity.PaymentMethod;
import com.ecommerce.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSummaryResponse(
        String orderNumber,
        UUID userId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        Instant paidAt,
        Instant createdAt
) {
}
