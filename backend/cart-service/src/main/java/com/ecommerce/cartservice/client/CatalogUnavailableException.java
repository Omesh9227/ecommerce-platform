package com.ecommerce.cartservice.client;

import com.ecommerce.cartservice.exception.ApiException;
import org.springframework.http.HttpStatus;

/** product-service could not be reached, timed out or failed. Maps to 503 when the request can't go on without it. */
public class CatalogUnavailableException extends ApiException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
        initCause(cause);
    }
}
