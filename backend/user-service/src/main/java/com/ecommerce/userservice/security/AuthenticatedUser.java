package com.ecommerce.userservice.security;

import com.ecommerce.userservice.entity.Role;

import java.util.UUID;

/** The principal stored in the SecurityContext for an authenticated request. Injected via @AuthenticationPrincipal. */
public record AuthenticatedUser(UUID id, String email, Role role) {
}
