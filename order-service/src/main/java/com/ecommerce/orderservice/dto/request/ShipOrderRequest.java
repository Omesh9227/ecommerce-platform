package com.ecommerce.orderservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Optional shipment details")
public record ShipOrderRequest(
        @Schema(example = "Delhivery")
        @Size(max = 100, message = "carrier must be at most 100 characters")
        String carrier,

        @Schema(example = "DLV123456789IN")
        @Size(max = 100, message = "trackingNumber must be at most 100 characters")
        String trackingNumber
) {
}
