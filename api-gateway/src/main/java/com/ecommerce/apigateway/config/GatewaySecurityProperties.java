package com.ecommerce.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Which paths are public and which need the ADMIN role (Ant-style patterns).
 * Every other routed path requires a valid JWT.
 */
@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(
        List<String> publicPaths,
        List<String> adminPaths
) {

    public GatewaySecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        adminPaths = adminPaths == null ? List.of() : List.copyOf(adminPaths);
    }
}
