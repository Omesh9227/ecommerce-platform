package com.ecommerce.userservice.dto.request;

import com.ecommerce.userservice.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PUT replaces all editable profile fields.
 * Email and password are intentionally not editable here (they need verification flows of their own).
 */
@Schema(description = "Update the current user's profile")
public record UpdateProfileRequest(

        @Schema(example = "Omesh")
        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name must be at most 50 characters")
        @Pattern(regexp = ValidationPatterns.NAME, message = "First name contains invalid characters")
        String firstName,

        @Schema(example = "Kumar")
        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name must be at most 50 characters")
        @Pattern(regexp = ValidationPatterns.NAME, message = "Last name contains invalid characters")
        String lastName,

        @Schema(example = "9123456780")
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = ValidationPatterns.PHONE, message = "Phone number must contain 10 to 15 digits")
        String phoneNumber
) {
}
