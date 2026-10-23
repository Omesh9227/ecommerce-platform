package com.ecommerce.wishlistservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/** Consistent error body for every failure, including security (401/403) errors. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error response")
public record ErrorResponse(
        @Schema(example = "2026-10-17T08:30:00Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "BAD_REQUEST") String error,
        @Schema(example = "Validation failed") String message,
        @Schema(example = "/api/v1/wishlist/items") String path,
        @Schema(description = "Field-level validation messages, present only for validation failures")
        Map<String, String> validationErrors
) {

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), status.name(), message, path, null);
    }

    public static ErrorResponse withValidationErrors(String message, String path, Map<String, String> errors) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return new ErrorResponse(Instant.now(), status.value(), status.name(), message, path, errors);
    }
}
