package com.ecommerce.recommendationservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** The customer opened a product page. */
public record ViewRequest(@NotNull(message = "productId is required") UUID productId) {
}
