package com.ecommerce.apigateway.dto;

import org.springframework.http.HttpStatus;

import java.time.Instant;

/** Same error shape as user-service, so clients see one format whether the gateway or a service rejects a request. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), status.name(), message, path);
    }
}
