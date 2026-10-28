package com.ecommerce.userservice.dto.response;

import com.ecommerce.userservice.entity.Role;

import java.util.UUID;

public record UserSummaryResponse(UUID userId, String email, Role role) {
}
