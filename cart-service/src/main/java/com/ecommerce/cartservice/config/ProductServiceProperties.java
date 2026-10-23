package com.ecommerce.cartservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** services.product-service.* - where product-service is and how long to wait for it. */
@Validated
@ConfigurationProperties(prefix = "services.product-service")
public record ProductServiceProperties(
        @NotBlank String url,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}
