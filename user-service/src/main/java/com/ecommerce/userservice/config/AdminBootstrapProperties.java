package com.ecommerce.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Credentials for the first ADMIN account, supplied via environment variables (never committed). */
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record AdminBootstrapProperties(
        String email,
        String password,
        String firstName,
        String lastName,
        String phoneNumber
) {
}
