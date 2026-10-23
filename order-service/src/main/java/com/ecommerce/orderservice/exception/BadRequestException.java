package com.ecommerce.orderservice.exception;

import org.springframework.http.HttpStatus;

/** A well-formed request that breaks a business rule (empty cart, invalid Idempotency-Key, ...). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
