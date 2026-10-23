package com.ecommerce.orderservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Places an order for everything in the caller's cart. Send it with an Idempotency-Key header. */
@Schema(description = "Place an order for the items in your cart")
public record CheckoutRequest(

        @Valid
        @NotNull(message = "shippingAddress is required")
        ShippingAddressRequest shippingAddress,

        @Schema(example = "1840.00", description = "Optional: the total the customer saw. If the real total differs "
                + "(a price changed), the order is not placed and 409 is returned so they can review it.")
        @DecimalMin(value = "0.00", message = "expectedTotal cannot be negative")
        BigDecimal expectedTotal
) {
}
