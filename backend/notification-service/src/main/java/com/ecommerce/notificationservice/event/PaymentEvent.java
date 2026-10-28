package com.ecommerce.notificationservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/** The fields of payment-service's events that notifications need (other fields are ignored). */
public record PaymentEvent(
        UUID eventId,
        String eventType,
        UUID orderId,
        String orderNumber,
        UUID userId,
        BigDecimal amount,
        String currency,
        String method,
        String cardBrand,
        String cardLast4,
        String reason
) {

    /** "VISA ending 4242" / "UPI" */
    public String describeMethod() {
        if ("CARD".equals(method) && cardLast4 != null) {
            return (cardBrand == null ? "card" : cardBrand) + " ending " + cardLast4;
        }
        return method == null ? "online payment" : method;
    }
}
