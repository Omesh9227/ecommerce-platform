package com.ecommerce.productservice.exception;

import org.springframework.http.HttpStatus;

/** A well-formed request that breaks a business rule (unknown category, minPrice > maxPrice, ...). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
