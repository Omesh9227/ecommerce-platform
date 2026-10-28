package com.ecommerce.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * A stand-in payment provider for development and tests, modelled on Stripe's test mode. No money moves.
 *
 * <pre>
 * Card 4242 4242 4242 4242 (or any other valid number)  -> succeeds
 * Card 4000 0000 0000 0002                              -> declined (card_declined)
 * Card 4000 0000 0000 9995                              -> declined (insufficient_funds)
 * Card 4000 0000 0000 0119                              -> provider error (the API answers 503)
 * UPI  anything@bank                                    -> succeeds
 * UPI  fail@bank (starts with "fail")                   -> declined (upi_declined)
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGateway {

    static final String DECLINED_CARD = "4000000000000002";
    static final String INSUFFICIENT_FUNDS_CARD = "4000000000009995";
    static final String PROCESSING_ERROR_CARD = "4000000000000119";

    @Override
    public ChargeResult charge(ChargeRequest request) {
        if (request.card() != null) {
            String number = request.card().number();
            if (PROCESSING_ERROR_CARD.equals(number)) {
                throw new GatewayUnavailableException("Simulated provider error");
            }
            if (DECLINED_CARD.equals(number)) {
                return ChargeResult.declined("card_declined", "Your card was declined.");
            }
            if (INSUFFICIENT_FUNDS_CARD.equals(number)) {
                return ChargeResult.declined("insufficient_funds", "Your card has insufficient funds.");
            }
        } else if (request.upiId() != null && request.upiId().toLowerCase(Locale.ROOT).startsWith("fail")) {
            return ChargeResult.declined("upi_declined", "The UPI payment was declined by your bank.");
        }
        String reference = "sim_pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        log.info("Simulated charge {} {} for {} -> {}", request.amount(), request.currency(), request.reference(), reference);
        return ChargeResult.success(reference);
    }

    @Override
    public String refund(String providerReference, BigDecimal amount, String currency) {
        String reference = "sim_ref_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        log.info("Simulated refund {} {} of {} -> {}", amount, currency, providerReference, reference);
        return reference;
    }
}
