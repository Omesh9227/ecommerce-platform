package com.ecommerce.paymentservice.mapper;

import com.ecommerce.paymentservice.dto.response.AttemptResponse;
import com.ecommerce.paymentservice.dto.response.PaymentResponse;
import com.ecommerce.paymentservice.dto.response.PaymentSummaryResponse;
import com.ecommerce.paymentservice.entity.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getOrderNumber(), p.getUserId(), p.getStatus(),
                p.getAmount(), p.getCurrency(), p.getMethod(), p.getCardBrand(), p.getCardLast4(), p.getUpiIdMasked(),
                p.getPaidAt(), p.getCancelledAt(), p.getRefundedAt(), p.getRefundReason(), p.getCreatedAt(),
                p.getAttempts().stream()
                        .map(a -> new AttemptResponse(a.getId(), a.getMethod(), a.getCardBrand(), a.getCardLast4(),
                                a.getUpiIdMasked(), a.getStatus(), a.getFailureCode(), a.getFailureReason(), a.getCreatedAt()))
                        .toList());
    }

    public PaymentSummaryResponse toSummary(Payment p) {
        return new PaymentSummaryResponse(p.getOrderNumber(), p.getUserId(), p.getStatus(), p.getAmount(),
                p.getCurrency(), p.getMethod(), p.getPaidAt(), p.getCreatedAt());
    }
}
