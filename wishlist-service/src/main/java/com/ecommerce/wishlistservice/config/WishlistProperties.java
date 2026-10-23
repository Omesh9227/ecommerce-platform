package com.ecommerce.wishlistservice.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * app.wishlist.* (from config-repo/wishlist-service.properties at runtime).
 *
 * @param maxItems            how many products one customer can save
 * @param priceDropMinPercent a price-drop email is sent only when the price fell at least this much (in %) below
 *                            the price the customer last saw - so a 1-rupee change doesn't send an email
 * @param currency            currency of all prices (store-wide)
 */
@Validated
@ConfigurationProperties(prefix = "app.wishlist")
public record WishlistProperties(
        @Positive int maxItems,
        @NotNull @DecimalMin("0.0") @DecimalMax("99.0") BigDecimal priceDropMinPercent,
        @NotBlank String currency
) {
}
