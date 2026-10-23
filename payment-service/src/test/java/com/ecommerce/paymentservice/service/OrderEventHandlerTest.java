package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.entity.AttemptStatus;
import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.entity.PaymentAttempt;
import com.ecommerce.paymentservice.entity.PaymentMethod;
import com.ecommerce.paymentservice.entity.PaymentStatus;
import com.ecommerce.paymentservice.event.EventTypes;
import com.ecommerce.paymentservice.event.OrderEvent;
import com.ecommerce.paymentservice.gateway.PaymentGateway;
import com.ecommerce.paymentservice.outbox.PaymentEventPublisher;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventHandlerTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway gateway;
    @Mock
    private PaymentEventPublisher eventPublisher;

    private OrderEventHandler handler;
    private final UUID orderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new OrderEventHandler(paymentRepository, gateway, eventPublisher,
                Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC));
    }

    private OrderEvent event(String type, BigDecimal total) {
        return new OrderEvent(UUID.randomUUID(), type, orderId, "ORD-1", userId, total, "INR", "Changed my mind", 1);
    }

    private Payment paid() {
        Payment payment = Payment.pending(orderId, "ORD-1", userId, new BigDecimal("1800.00"), "INR");
        PaymentAttempt attempt = new PaymentAttempt("key-12345678", PaymentMethod.UPI, null, null, "om***@okhdfc",
                payment.getAmount(), AttemptStatus.SUCCEEDED, null, null, "sim_pay_1", Instant.now());
        payment.addAttempt(attempt);
        payment.markSucceeded(attempt, Instant.now());
        return payment;
    }

    @Test
    void orderPlaced_createsAPendingPaymentForTheOrderTotal_once() {
        when(paymentRepository.existsByOrderId(orderId)).thenReturn(false);

        handler.handle(event(EventTypes.ORDER_PLACED, new BigDecimal("1800.00")));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("1800.00");
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);

        when(paymentRepository.existsByOrderId(orderId)).thenReturn(true);
        handler.handle(event(EventTypes.ORDER_PLACED, new BigDecimal("1800.00")));
        verify(paymentRepository).save(any()); // still once
    }

    @Test
    void orderPlaced_withoutAnAmount_isRejected() {
        assertThatThrownBy(() -> handler.handle(event(EventTypes.ORDER_PLACED, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderCancelled_beforePayment_cancelsThePayment() {
        Payment payment = Payment.pending(orderId, "ORD-1", userId, new BigDecimal("1800.00"), "INR");
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));

        handler.handle(event(EventTypes.ORDER_CANCELLED, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verifyNoInteractions(gateway, eventPublisher);
    }

    @Test
    void orderCancelled_afterPayment_refundsInFull() {
        Payment payment = paid();
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
        when(gateway.refund("sim_pay_1", new BigDecimal("1800.00"), "INR")).thenReturn("sim_ref_1");

        handler.handle(event(EventTypes.ORDER_CANCELLED, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundReason()).isEqualTo("Order cancelled: Changed my mind");
        verify(eventPublisher).publish(eq(EventTypes.PAYMENT_REFUNDED), eq(payment), any(), any());

        // A repeated event refunds nothing more
        handler.handle(event(EventTypes.ORDER_CANCELLED, null));
        verify(gateway).refund(any(), any(), any());
    }

    @Test
    void otherOrderEvents_areIgnored() {
        handler.handle(event("ORDER_SHIPPED", null));
        verifyNoInteractions(paymentRepository, gateway, eventPublisher);
        verify(paymentRepository, never()).save(any());
    }
}
