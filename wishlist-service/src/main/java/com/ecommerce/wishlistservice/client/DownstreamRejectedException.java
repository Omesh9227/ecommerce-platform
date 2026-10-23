package com.ecommerce.wishlistservice.client;

import com.ecommerce.wishlistservice.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Another service refused the request (4xx), e.g. cart-service: "Pixel 8 is out of stock" (409).
 * Passed on to the client with the same status and the other service's own message.
 */
public class DownstreamRejectedException extends ApiException {

    public DownstreamRejectedException(HttpStatus status, String message) {
        super(status, message);
    }
}
