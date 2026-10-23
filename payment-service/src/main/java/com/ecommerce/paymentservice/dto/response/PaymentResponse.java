package com.ecommerce.paymentservice.dto.response;

import com.ecommerce.paymentservice.entity.PaymentMethod;
import com.ecommerce.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A payment and every attempt to pay it (oldest first). No card data beyond brand and last 4 digits. */
public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        String orderNumber,
        UUID userId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        String cardBrand,
        String cardLast4,
        String upiIdMasked,
        Instant paidAt,
        Instant cancelledAt,
        Instant refundedAt,
        String refundReason,
        Instant createdAt,
        List<AttemptResponse> attempts
) {
}
