package com.ecommerce.productservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.catalog.* settings. All prices in the catalog are in this currency. */
@ConfigurationProperties(prefix = "app.catalog")
public record CatalogProperties(String currency) {

    public CatalogProperties {
        currency = (currency == null || currency.isBlank()) ? "INR" : currency;
    }
}
