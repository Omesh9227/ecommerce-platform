package com.ecommerce.shippingservice.security;

import java.util.UUID;

/** The caller, taken from a verified JWT. Injected into controllers via @AuthenticationPrincipal. */
public record AuthenticatedUser(UUID id, String email, String role) {

    public static final String ROLE_ADMIN = "ADMIN";

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    /** Null-safe helper for controllers: anonymous callers are never admins. */
    public static boolean isAdmin(AuthenticatedUser user) {
        return user != null && user.isAdmin();
    }
}
