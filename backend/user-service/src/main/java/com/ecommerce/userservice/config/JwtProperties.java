package com.ecommerce.userservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bound from security.jwt.* in application.properties. The app fails fast on startup if these are missing. */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Positive long expirationSeconds,
        @NotBlank String issuer
) {
}
