package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.event.EventTypes;
import com.ecommerce.orderservice.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reacts to payment-service:
 * - PAYMENT_SUCCEEDED: the order is confirmed (stock becomes a sale);
 * - PAYMENT_REFUNDED: the refund appears on the cancelled order's timeline;
 * - PAYMENT_FAILED: nothing - the order keeps waiting for payment until its payment window ends.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventHandler {

    private final OrderLifecycleService lifecycleService;

    public void handle(PaymentEvent event) {
        if (event.eventType() == null) {
            throw new IllegalArgumentException("Payment event " + event.eventId() + " has no type");
        }
        switch (event.eventType()) {
            case EventTypes.PAYMENT_SUCCEEDED -> {
                requireOrderId(event);
                lifecycleService.markPaid(event.orderId(), "Paid by " + event.describeMethod());
            }
            case EventTypes.PAYMENT_REFUNDED -> {
                requireOrderId(event);
                lifecycleService.recordRefund(event.orderId(),
                        "Refund of " + event.amount() + " " + event.currency() + " completed");
            }
            default -> log.debug("Ignoring payment event {} for order {}", event.eventType(), event.orderNumber());
        }
    }

    private static void requireOrderId(PaymentEvent event) {
        if (event.orderId() == null) {
            throw new IllegalArgumentException("Payment event " + event.eventId() + " has no orderId");
        }
    }
}
