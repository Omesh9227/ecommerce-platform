package com.ecommerce.productservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base class for business exceptions that map directly to an HTTP status. */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
