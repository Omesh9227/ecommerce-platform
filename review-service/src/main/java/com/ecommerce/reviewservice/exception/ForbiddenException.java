package com.ecommerce.reviewservice.exception;

import org.springframework.http.HttpStatus;

/** Logged in, but not allowed to do this (e.g. review a product you never received, when that's required). */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
