package com.ecommerce.userservice.dto.response;

import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.Role;

import java.time.Instant;
import java.util.UUID;

/** Full user profile. Never contains the password hash. */
public record UserResponse(
        UUID userId,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        Role role,
        AccountStatus accountStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
