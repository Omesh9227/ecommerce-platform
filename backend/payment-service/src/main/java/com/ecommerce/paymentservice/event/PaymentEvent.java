package com.ecommerce.paymentservice.event;

import com.ecommerce.paymentservice.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on payment-events (key = orderId, so one order's events stay in order).
 * paymentVersion increases with every change; consumers can ignore repeated events.
 */
public record PaymentEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID paymentId,
        UUID orderId,
        String orderNumber,
        UUID userId,
        String status,
        BigDecimal amount,
        String currency,
        String method,
        String cardBrand,
        String cardLast4,
        String reason,
        long paymentVersion
) {

    public static PaymentEvent of(String eventType, Payment payment, String reason, Instant now) {
        return new PaymentEvent(UUID.randomUUID(), eventType, now, payment.getId(), payment.getOrderId(),
                payment.getOrderNumber(), payment.getUserId(), payment.getStatus().name(), payment.getAmount(),
                payment.getCurrency(), payment.getMethod() == null ? null : payment.getMethod().name(),
                payment.getCardBrand(), payment.getCardLast4(), reason,
                payment.getVersion() == null ? 0 : payment.getVersion());
    }
}
