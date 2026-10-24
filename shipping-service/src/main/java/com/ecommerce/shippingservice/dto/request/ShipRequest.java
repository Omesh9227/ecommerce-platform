package com.ecommerce.shippingservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Hand a packed parcel to a carrier.
 *
 * @param trackingNumber the carrier's number, if it gave one; otherwise we generate one
 * @param location       where it was handed over, e.g. "Bengaluru warehouse"
 */
public record ShipRequest(
        @Schema(example = "Delhivery", description = "One of the carriers in app.shipping.carriers")
        @NotBlank(message = "carrier is required") @Size(max = 50, message = "carrier must be at most 50 characters")
        String carrier,
        @Schema(example = "DLV1234567890")
        @Pattern(regexp = "^[A-Za-z0-9-]{6,40}$", message = "trackingNumber must be 6-40 letters, digits or dashes")
        String trackingNumber,
        @Schema(example = "Bengaluru warehouse")
        @Size(max = 100, message = "location must be at most 100 characters")
        String location
) {
}
