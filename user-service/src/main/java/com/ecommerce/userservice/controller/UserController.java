package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.config.OpenApiConfig;
import com.ecommerce.userservice.dto.request.UpdateProfileRequest;
import com.ecommerce.userservice.dto.response.ApiResult;
import com.ecommerce.userservice.dto.response.ErrorResponse;
import com.ecommerce.userservice.dto.response.UserResponse;
import com.ecommerce.userservice.security.AuthenticatedUser;
import com.ecommerce.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "The authenticated user's own profile")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get the current user's profile")
    @ApiResponse(responseCode = "200", description = "Profile returned")
    @GetMapping("/me")
    public ResponseEntity<ApiResult<UserResponse>> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("User fetched successfully", userService.getCurrentUser(currentUser.id())));
    }

    @Operation(summary = "Update the current user's profile",
            description = "Replaces first name, last name and phone number. Email and password cannot be changed here.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/me")
    public ResponseEntity<ApiResult<UserResponse>> updateCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResult.of("Profile updated successfully",
                userService.updateCurrentUser(currentUser.id(), request)));
    }
}
