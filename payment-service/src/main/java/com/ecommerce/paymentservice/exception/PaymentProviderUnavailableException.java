package com.ecommerce.paymentservice.exception;

import org.springframework.http.HttpStatus;

/** The payment provider couldn't be reached. Nothing was charged; the client can retry with the same key. */
public class PaymentProviderUnavailableException extends ApiException {

    public PaymentProviderUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "The payment provider is temporarily unavailable. You were not charged; please try again.");
    }
}
