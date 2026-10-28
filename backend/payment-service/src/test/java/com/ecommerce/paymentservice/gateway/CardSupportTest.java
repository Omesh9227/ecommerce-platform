package com.ecommerce.paymentservice.gateway;

import com.ecommerce.paymentservice.gateway.PaymentGateway.ChargeRequest;
import com.ecommerce.paymentservice.gateway.PaymentGateway.ChargeResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardSupportTest {

    @Test
    void luhnCheck() {
        assertThat(CardSupport.isValidNumber("4242424242424242")).isTrue();
        assertThat(CardSupport.isValidNumber("5555555555554444")).isTrue();
        assertThat(CardSupport.isValidNumber("4242424242424241")).isFalse();
        assertThat(CardSupport.isValidNumber("42424242")).isFalse();
        assertThat(CardSupport.isValidNumber(null)).isFalse();
        assertThat(CardSupport.normalize("4242 4242-4242 4242")).isEqualTo("4242424242424242");
    }

    @Test
    void expiry_isTheEndOfTheMonth() {
        YearMonth now = YearMonth.of(2026, 10);
        assertThat(CardSupport.isExpired(10, 2026, now)).isFalse();
        assertThat(CardSupport.isExpired(9, 2026, now)).isTrue();
        assertThat(CardSupport.isExpired(1, 2030, now)).isFalse();
    }

    @Test
    void brandLast4AndMaskedUpi() {
        assertThat(CardSupport.brand("4242424242424242")).isEqualTo("VISA");
        assertThat(CardSupport.brand("5555555555554444")).isEqualTo("MASTERCARD");
        assertThat(CardSupport.brand("378282246310005")).isEqualTo("AMEX");
        assertThat(CardSupport.brand("6522123412341234")).isEqualTo("RUPAY");
        assertThat(CardSupport.last4("4242424242424242")).isEqualTo("4242");
        assertThat(CardSupport.maskUpi("omesh.kumar@okhdfc")).isEqualTo("om*********@okhdfc");
        assertThat(CardSupport.maskUpi("ab@upi")).isEqualTo("ab@upi");
    }

    @Test
    void simulatedGateway_outcomes() {
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();
        BigDecimal amount = new BigDecimal("100.00");

        ChargeResult ok = gateway.charge(new ChargeRequest("ORD-1", amount, "INR", card("4242424242424242"), null));
        assertThat(ok.success()).isTrue();
        assertThat(ok.providerReference()).startsWith("sim_pay_");

        assertThat(gateway.charge(new ChargeRequest("ORD-1", amount, "INR", card("4000000000000002"), null)).failureCode())
                .isEqualTo("card_declined");
        assertThat(gateway.charge(new ChargeRequest("ORD-1", amount, "INR", card("4000000000009995"), null)).failureCode())
                .isEqualTo("insufficient_funds");
        assertThatThrownBy(() -> gateway.charge(new ChargeRequest("ORD-1", amount, "INR", card("4000000000000119"), null)))
                .isInstanceOf(PaymentGateway.GatewayUnavailableException.class);

        assertThat(gateway.charge(new ChargeRequest("ORD-1", amount, "INR", null, "omesh@okhdfc")).success()).isTrue();
        assertThat(gateway.charge(new ChargeRequest("ORD-1", amount, "INR", null, "fail@okhdfc")).failureCode())
                .isEqualTo("upi_declined");
        assertThat(gateway.refund("sim_pay_x", amount, "INR")).startsWith("sim_ref_");
    }

    @Test
    void cardData_neverAppearsInToString() {
        ChargeRequest.Card card = card("4242424242424242");
        assertThat(card.toString()).doesNotContain("424242424242").doesNotContain("123");
        assertThat(new com.ecommerce.paymentservice.dto.request.CardRequest("4242 4242 4242 4242", 12, 2030, "123", "X").toString())
                .doesNotContain("4242").doesNotContain("123");
    }

    private static ChargeRequest.Card card(String number) {
        return new ChargeRequest.Card(number, 12, 2030, "123", "Omesh");
    }
}
