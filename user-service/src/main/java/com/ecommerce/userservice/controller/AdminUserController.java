package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.config.OpenApiConfig;
import com.ecommerce.userservice.dto.request.UpdateAccountStatusRequest;
import com.ecommerce.userservice.dto.response.ApiResult;
import com.ecommerce.userservice.dto.response.ErrorResponse;
import com.ecommerce.userservice.dto.response.PageResponse;
import com.ecommerce.userservice.dto.response.UserResponse;
import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.security.AuthenticatedUser;
import com.ecommerce.userservice.service.AdminUserService;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin - Users", description = "User management (ADMIN role required)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "List users",
            description = "Paginated, newest first by default. Optional filters: role, status. "
                    + "Example: ?role=CUSTOMER&status=ACTIVE&page=0&size=20&sort=createdAt,desc")
    @ApiResponse(responseCode = "200", description = "Users returned")
    @GetMapping
    public ResponseEntity<ApiResult<PageResponse<UserResponse>>> listUsers(
            @RequestParam(name = "role", required = false) Role role,
            @RequestParam(name = "status", required = false) AccountStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Users fetched successfully",
                adminUserService.listUsers(role, status, pageable)));
    }

    @Operation(summary = "Get a user by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User returned"),
            @ApiResponse(responseCode = "400", description = "Malformed UUID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<UserResponse>> getUser(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResult.of("User fetched successfully", adminUserService.getUser(id)));
    }

    @Operation(summary = "Change a user's account status",
            description = "Non-ACTIVE users cannot log in, and their existing tokens stop working immediately. "
                    + "Admins cannot change their own status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status or changing own status",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResult<UserResponse>> updateStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentAdmin,
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        return ResponseEntity.ok(ApiResult.of("Account status updated successfully",
                adminUserService.updateAccountStatus(currentAdmin.id(), id, request)));
    }
}
