package com.ecommerce.reviewservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** services.* - the services review-service calls directly (never through the gateway). */
@Validated
@ConfigurationProperties(prefix = "services")
public record DownstreamServicesProperties(
        @Valid @NotNull Service productService,
        @Valid @NotNull Service userService,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    public record Service(@NotBlank String url) {
    }
}
