package com.ecommerce.paymentservice.dto.response;

import com.ecommerce.paymentservice.entity.AttemptStatus;
import com.ecommerce.paymentservice.entity.PaymentMethod;

import java.time.Instant;
import java.util.UUID;

public record AttemptResponse(
        UUID attemptId,
        PaymentMethod method,
        String cardBrand,
        String cardLast4,
        String upiIdMasked,
        AttemptStatus status,
        String failureCode,
        String failureReason,
        Instant createdAt
) {
}
