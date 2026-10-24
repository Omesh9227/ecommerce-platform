package com.ecommerce.shippingservice.dto.request;

import com.ecommerce.shippingservice.entity.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A carrier scan: where the parcel is now. IN_TRANSIT needs a location; DELIVERY_FAILED needs a note (the reason).
 */
public record TrackingUpdateRequest(
        @Schema(example = "IN_TRANSIT", allowableValues = {"IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERY_FAILED", "DELIVERED"})
        @NotNull(message = "status is required")
        ShipmentStatus status,
        @Schema(example = "Hyderabad hub")
        @Size(max = 100, message = "location must be at most 100 characters")
        String location,
        @Schema(example = "Customer not available")
        @Size(max = 200, message = "note must be at most 200 characters")
        String note
) {
}
