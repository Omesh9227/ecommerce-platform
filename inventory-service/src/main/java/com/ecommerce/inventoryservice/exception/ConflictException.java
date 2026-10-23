package com.ecommerce.inventoryservice.exception;

import org.springframework.http.HttpStatus;

/** The request is valid but clashes with existing data (duplicate SKU, category still in use, ...). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
