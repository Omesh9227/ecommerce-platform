package com.ecommerce.inventoryservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Hold stock for an order. All items are reserved, or none are.
 * Sending the same orderReference again returns the existing reservation (safe to retry).
 */
@Schema(description = "Reserve stock for an order")
public record ReservationRequest(

        @Schema(example = "ORD-2026-0001")
        @NotBlank(message = "orderReference is required")
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,100}$", message = "orderReference must be 3-100 letters, digits, '-' or '_'")
        String orderReference,

        @NotEmpty(message = "At least one item is required")
        @Size(max = 50, message = "At most 50 items per reservation")
        List<@Valid @NotNull(message = "Item must not be null") Item> items
) {

    public record Item(
            @NotNull(message = "productId is required")
            UUID productId,

            @Schema(example = "2")
            @NotNull(message = "quantity is required")
            @Min(value = 1, message = "quantity must be at least 1")
            @Max(value = 1000, message = "quantity must be at most 1000")
            Integer quantity
    ) {
    }
}
