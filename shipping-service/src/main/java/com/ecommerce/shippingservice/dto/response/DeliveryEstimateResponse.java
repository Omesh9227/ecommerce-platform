package com.ecommerce.shippingservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** "Delivery by Tue, 27 Oct" for a PIN code, if ordered today. */
public record DeliveryEstimateResponse(
        @Schema(example = "110001") String postalCode,
        @Schema(example = "5") int deliveryDays,
        @Schema(example = "2026-10-29") LocalDate estimatedDeliveryDate,
        @Schema(example = "Delivery by Thu, 29 Oct") String message
) {
}
