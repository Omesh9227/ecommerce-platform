package com.ecommerce.cartservice.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** app.cart.* - cart rules. */
@Validated
@ConfigurationProperties(prefix = "app.cart")
public record CartProperties(
        /* Different products in one cart. Max 50 = what product-service's lookup accepts in one call. */
        @Min(1) @Max(50) int maxItems,
        /* Units of one product per cart (stock can lower this further). */
        @Min(1) @Max(100) int maxQuantityPerItem,
        /* A cart nobody touches for this long is deleted by Redis. Every change or view restarts the clock. */
        @NotNull Duration ttl,
        /* Shown when prices could not be refreshed (normally taken from product-service). */
        @NotBlank String currency
) {
}
