package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.event.EventTypes;
import com.ecommerce.paymentservice.event.OrderEvent;
import com.ecommerce.paymentservice.gateway.PaymentGateway;
import com.ecommerce.paymentservice.outbox.PaymentEventPublisher;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Reacts to order-service:
 * - ORDER_PLACED: a PENDING payment is created with the order's total (the only place an amount comes from);
 * - ORDER_CANCELLED: a PENDING payment is cancelled; a SUCCEEDED one is refunded in full (PAYMENT_REFUNDED).
 * Every branch is safe to run twice (Kafka delivers at least once).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventHandler {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final PaymentEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public void handle(OrderEvent event) {
        if (EventTypes.ORDER_PLACED.equals(event.eventType())) {
            onOrderPlaced(event);
        } else if (EventTypes.ORDER_CANCELLED.equals(event.eventType())) {
            onOrderCancelled(event);
        }
        // other order events don't concern payments
    }

    private void onOrderPlaced(OrderEvent event) {
        if (event.orderId() == null || event.orderNumber() == null || event.userId() == null
                || event.totalAmount() == null || event.totalAmount().compareTo(BigDecimal.ZERO) <= 0 || event.currency() == null) {
            // Not retryable: goes to the dead-letter topic
            throw new IllegalArgumentException("ORDER_PLACED event " + event.eventId() + " is missing required fields");
        }
        if (paymentRepository.existsByOrderId(event.orderId())) {
            return; // duplicate event
        }
        paymentRepository.save(Payment.pending(event.orderId(), event.orderNumber(), event.userId(),
                event.totalAmount(), event.currency()));
        log.info("Payment pending for order {} ({} {})", event.orderNumber(), event.totalAmount(), event.currency());
    }

    private void onOrderCancelled(OrderEvent event) {
        Payment payment = paymentRepository.findByOrderIdForUpdate(event.orderId()).orElse(null);
        if (payment == null) {
            log.warn("ORDER_CANCELLED for unknown order {}; nothing to do", event.orderNumber());
            return;
        }
        Instant now = clock.instant();
        switch (payment.getStatus()) {
            case PENDING -> {
                payment.markCancelled(now);
                log.info("Payment for order {} cancelled (never paid)", payment.getOrderNumber());
            }
            case SUCCEEDED -> {
                // Throws if the provider is down: the event is retried, so the refund is never lost
                String refundReference = gateway.refund(payment.getProviderReference(), payment.getAmount(), payment.getCurrency());
                String reason = "Order cancelled" + (event.reason() == null ? "" : ": " + event.reason());
                payment.markRefunded(refundReference, reason, now);
                paymentRepository.saveAndFlush(payment);
                eventPublisher.publish(EventTypes.PAYMENT_REFUNDED, payment, reason, now);
                log.info("Refunded {} {} for cancelled order {}", payment.getAmount(), payment.getCurrency(), payment.getOrderNumber());
            }
            case CANCELLED, REFUNDED -> {
                // already done
            }
        }
    }
}
