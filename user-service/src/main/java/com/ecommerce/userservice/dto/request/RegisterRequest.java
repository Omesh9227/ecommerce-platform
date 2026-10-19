package com.ecommerce.userservice.dto.request;

import com.ecommerce.userservice.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Customer registration request")
public record RegisterRequest(

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

        @Schema(example = "omesh@example.com")
        @NotBlank(message = "Email is required")
        @Size(max = 255, message = "Email must be at most 255 characters")
        @Email(regexp = ValidationPatterns.EMAIL, message = "Email must be a valid email address")
        String email,

        @Schema(example = "9876543210")
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = ValidationPatterns.PHONE, message = "Phone number must contain 10 to 15 digits")
        String phoneNumber,

        @Schema(example = "Password@123")
        @NotBlank(message = "Password is required")
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String password
) {
}
