package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Another service could not be reached, timed out, or failed (5xx). Shown to the client as 503. */
public class DownstreamUnavailableException extends ApiException {

    public DownstreamUnavailableException(String service, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                "Checkout is temporarily unavailable (" + service + " is not responding). Please try again shortly.");
        if (cause != null) {
            initCause(cause);
        }
    }
}
