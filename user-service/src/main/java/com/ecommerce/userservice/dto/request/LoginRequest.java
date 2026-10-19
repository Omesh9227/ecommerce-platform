package com.ecommerce.userservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** No password-strength check here: login only verifies what was stored at registration. */
@Schema(description = "Login request")
public record LoginRequest(

        @Schema(example = "omesh@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @Schema(example = "Password@123")
        @NotBlank(message = "Password is required")
        @Size(max = 64, message = "Password must be at most 64 characters")
        String password
) {
}
