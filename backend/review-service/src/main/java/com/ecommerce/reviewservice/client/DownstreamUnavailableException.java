package com.ecommerce.reviewservice.client;

import com.ecommerce.reviewservice.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Another service could not be reached, timed out, or failed (5xx). Shown to the client as 503. */
public class DownstreamUnavailableException extends ApiException {

    public DownstreamUnavailableException(String what, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, what + " is temporarily unavailable. Please try again shortly.");
        if (cause != null) {
            initCause(cause);
        }
    }
}
