package com.ecommerce.paymentservice.gateway;

import java.math.BigDecimal;

/**
 * The payment provider. Today: {@link SimulatedPaymentGateway}. A Razorpay or Stripe adapter implements the same
 * interface later; nothing else in the service changes.
 */
public interface PaymentGateway {

    /** What to charge. Card data is passed through and never stored. */
    record ChargeRequest(String reference, BigDecimal amount, String currency, Card card, String upiId) {

        public record Card(String number, int expiryMonth, int expiryYear, String cvv, String holderName) {

            @Override
            public String toString() { // never print card data, even by accident
                return "Card[****" + (number == null || number.length() < 4 ? "" : number.substring(number.length() - 4)) + "]";
            }
        }
    }

    /** @param failureCode machine-readable (e.g. card_declined) when not successful */
    record ChargeResult(boolean success, String providerReference, String failureCode, String failureMessage) {

        public static ChargeResult success(String providerReference) {
            return new ChargeResult(true, providerReference, null, null);
        }

        public static ChargeResult declined(String failureCode, String failureMessage) {
            return new ChargeResult(false, null, failureCode, failureMessage);
        }
    }

    /** A declined charge is a normal result; this is thrown only when the provider can't be reached. */
    class GatewayUnavailableException extends RuntimeException {
        public GatewayUnavailableException(String message) {
            super(message);
        }
    }

    ChargeResult charge(ChargeRequest request);

    /** Refunds a successful charge in full. @return the provider's refund reference */
    String refund(String providerReference, BigDecimal amount, String currency);
}
