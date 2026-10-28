package com.ecommerce.inventoryservice.config;

import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** app.inventory.* - business defaults. */
@Validated
@ConfigurationProperties(prefix = "app.inventory")
public record InventoryProperties(
        /* Threshold given to new products; alerts fire when available stock drops to this level or below. */
        @PositiveOrZero int defaultLowStockThreshold
) {
}
