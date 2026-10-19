package com.ecommerce.userservice.exception;

import org.springframework.http.HttpStatus;

/** A well-formed request that breaks a business rule (e.g. an admin suspending their own account). */
public class InvalidOperationException extends ApiException {

    public InvalidOperationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
