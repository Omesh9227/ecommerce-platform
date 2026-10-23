package com.ecommerce.cartservice.exception;

import org.springframework.http.HttpStatus;

/** A well-formed request that breaks a business rule (quantity over the limit, cart full, ...). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
