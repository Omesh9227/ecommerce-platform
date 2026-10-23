package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.request.CardRequest;
import com.ecommerce.paymentservice.dto.request.PayRequest;
import com.ecommerce.paymentservice.entity.AttemptStatus;
import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.entity.PaymentMethod;
import com.ecommerce.paymentservice.entity.PaymentStatus;
import com.ecommerce.paymentservice.event.EventTypes;
import com.ecommerce.paymentservice.exception.BadRequestException;
import com.ecommerce.paymentservice.exception.ConflictException;
import com.ecommerce.paymentservice.exception.PaymentProviderUnavailableException;
import com.ecommerce.paymentservice.exception.ResourceNotFoundException;
import com.ecommerce.paymentservice.gateway.PaymentGateway;
import com.ecommerce.paymentservice.gateway.PaymentGateway.ChargeResult;
import com.ecommerce.paymentservice.mapper.PaymentMapper;
import com.ecommerce.paymentservice.outbox.PaymentEventPublisher;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String KEY = "pay-attempt-1";

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway gateway;
    @Mock
    private PaymentEventPublisher eventPublisher;

    private PaymentService paymentService;
    private final AuthenticatedUser customer = new AuthenticatedUser(UUID.randomUUID(), "c@example.com", "CUSTOMER");
    private Payment payment;

    private static PayRequest card(String number) {
        return new PayRequest(PaymentMethod.CARD, new CardRequest(number, 12, 2030, "123", "Omesh"), null);
    }

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, gateway, eventPublisher, new PaymentMapper(),
                Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC));
        payment = Payment.pending(UUID.randomUUID(), "ORD-1", customer.id(), new BigDecimal("1800.00"), "INR");
    }

    private void paymentExists() {
        when(paymentRepository.findByOrderNumberForUpdate("ORD-1")).thenReturn(Optional.of(payment));
    }

    @Test
    void successfulCard_marksPaid_storesOnlyBrandAndLast4_andPublishes() {
        paymentExists();
        when(gateway.charge(any())).thenReturn(ChargeResult.success("sim_pay_1"));

        PayResult result = paymentService.pay("ORD-1", customer, KEY, card("4242 4242 4242 4242"));

        assertThat(result.succeeded()).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getCardBrand()).isEqualTo("VISA");
        assertThat(payment.getCardLast4()).isEqualTo("4242");
        assertThat(payment.getAttempts()).singleElement().satisfies(a -> {
            assertThat(a.getStatus()).isEqualTo(AttemptStatus.SUCCEEDED);
            assertThat(a.getAmount()).isEqualByComparingTo("1800.00");
        });
        verify(eventPublisher).publish(eq(EventTypes.PAYMENT_SUCCEEDED), eq(payment), isNull(), any());
    }

    @Test
    void declinedCard_isRecorded_andThePaymentStaysPending() {
        paymentExists();
        when(gateway.charge(any())).thenReturn(ChargeResult.declined("card_declined", "Your card was declined."));

        PayResult result = paymentService.pay("ORD-1", customer, KEY, card("4000000000000002"));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).isEqualTo("Your card was declined.");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAttempts()).singleElement().extracting(a -> a.getFailureCode()).isEqualTo("card_declined");
        verify(eventPublisher).publish(eq(EventTypes.PAYMENT_FAILED), eq(payment), eq("Your card was declined."), any());
    }

    @Test
    void sameKeyAgain_returnsTheSameAttempt_withoutChargingAgain() {
        paymentExists();
        when(gateway.charge(any())).thenReturn(ChargeResult.declined("card_declined", "Your card was declined."));

        paymentService.pay("ORD-1", customer, KEY, card("4000000000000002"));
        PayResult again = paymentService.pay("ORD-1", customer, KEY, card("4000000000000002"));

        assertThat(again.succeeded()).isFalse();
        verify(gateway, times(1)).charge(any());
        assertThat(payment.getAttempts()).hasSize(1);
    }

    @Test
    void alreadyPaid_orCancelled_isConflict() {
        paymentExists();
        when(gateway.charge(any())).thenReturn(ChargeResult.success("sim_pay_1"));
        paymentService.pay("ORD-1", customer, KEY, card("4242424242424242"));

        assertThatThrownBy(() -> paymentService.pay("ORD-1", customer, "pay-attempt-2", card("4242424242424242")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already paid");

        Payment cancelled = Payment.pending(UUID.randomUUID(), "ORD-2", customer.id(), BigDecimal.TEN, "INR");
        cancelled.markCancelled(Instant.now());
        when(paymentRepository.findByOrderNumberForUpdate("ORD-2")).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> paymentService.pay("ORD-2", customer, KEY, card("4242424242424242")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void someoneElsesOrder_isNotFound() {
        paymentExists();
        AuthenticatedUser other = new AuthenticatedUser(UUID.randomUUID(), "o@example.com", "CUSTOMER");

        assertThatThrownBy(() -> paymentService.pay("ORD-1", other, KEY, card("4242424242424242")))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void invalidInput_isRejectedBeforeAnythingHappens() {
        assertThatThrownBy(() -> paymentService.pay("ORD-1", customer, null, card("4242424242424242")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Idempotency-Key");
        assertThatThrownBy(() -> paymentService.pay("ORD-1", customer, KEY, card("4242424242424241")))
                .isInstanceOf(BadRequestException.class).hasMessage("The card number is not valid");
        assertThatThrownBy(() -> paymentService.pay("ORD-1", customer, KEY,
                new PayRequest(PaymentMethod.CARD, new CardRequest("4242424242424242", 9, 2026, "123", null), null)))
                .isInstanceOf(BadRequestException.class).hasMessage("The card has expired");
        assertThatThrownBy(() -> paymentService.pay("ORD-1", customer, KEY, new PayRequest(PaymentMethod.CARD, null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> paymentService.pay("ORD-1", customer, KEY, new PayRequest(PaymentMethod.UPI, null, null)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(paymentRepository, gateway, eventPublisher);
    }

    @Test
    void providerDown_isServiceUnavailable_andNothingIsRecorded() {
        paymentExists();
        when(gateway.charge(any())).thenThrow(new PaymentGateway.GatewayUnavailableException("down"));

        assertThatThrownBy(() -> paymentService.pay("ORD-1", customer, KEY, card("4000000000000119")))
                .isInstanceOf(PaymentProviderUnavailableException.class);
        assertThat(payment.getAttempts()).isEmpty();
        verify(eventPublisher, never()).publish(any(), any(), any(), any());
    }
}
