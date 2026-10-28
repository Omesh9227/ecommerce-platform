package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.response.PaymentResponse;

/**
 * The outcome of a payment attempt. A declined card is not an exception: the attempt is saved
 * (so a retry with the same Idempotency-Key gets the same answer) and the API answers 402.
 */
public record PayResult(PaymentResponse payment, boolean succeeded, String failureMessage) {
}
