package com.ecommerce.orderservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

/** app.order.* - checkout and lifecycle rules. */
@Validated
@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(
        @NotBlank String currency,
        /* Charged when the subtotal is below freeShippingThreshold. */
        @NotNull @PositiveOrZero BigDecimal shippingFee,
        @NotNull @PositiveOrZero BigDecimal freeShippingThreshold,
        /* How long a placed order waits for payment before it is cancelled and its stock released. */
        @NotNull Duration paymentWindow,
        /* A checkout still in CREATED after this long is treated as failed and its stock released. */
        @NotNull Duration stuckCheckoutTimeout,
        @Positive long expiryCheckIntervalMs
) {

    public BigDecimal shippingFeeFor(BigDecimal subtotal) {
        return subtotal.compareTo(freeShippingThreshold) >= 0 ? BigDecimal.ZERO.setScale(2) : shippingFee.setScale(2);
    }
}
