package com.ecommerce.orderservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Delivery address (India for now)")
public record ShippingAddressRequest(

        @Schema(example = "Omesh Kumar")
        @NotBlank(message = "fullName is required")
        @Size(max = 100, message = "fullName must be at most 100 characters")
        String fullName,

        @Schema(example = "9876543210")
        @NotBlank(message = "phone is required")
        @Pattern(regexp = "^[6-9][0-9]{9}$", message = "phone must be a 10-digit Indian mobile number")
        String phone,

        @Schema(example = "Flat 12B, Green Residency")
        @NotBlank(message = "line1 is required")
        @Size(max = 200, message = "line1 must be at most 200 characters")
        String line1,

        @Schema(example = "MG Road")
        @Size(max = 200, message = "line2 must be at most 200 characters")
        String line2,

        @Schema(example = "Bengaluru")
        @NotBlank(message = "city is required")
        @Size(max = 100, message = "city must be at most 100 characters")
        String city,

        @Schema(example = "Karnataka")
        @NotBlank(message = "state is required")
        @Size(max = 100, message = "state must be at most 100 characters")
        String state,

        @Schema(example = "560001")
        @NotBlank(message = "postalCode is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "postalCode must be a 6-digit PIN code")
        String postalCode
) {
}
