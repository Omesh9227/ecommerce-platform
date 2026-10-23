package com.ecommerce.wishlistservice.exception;

import org.springframework.http.HttpStatus;

/** A well-formed request that breaks a business rule (invalid card, missing Idempotency-Key, ...). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
