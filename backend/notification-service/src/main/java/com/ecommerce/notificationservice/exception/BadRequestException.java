package com.ecommerce.notificationservice.exception;

import org.springframework.http.HttpStatus;

/** A well-formed request that can't be served (e.g. an unknown sort field). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
