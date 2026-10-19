package com.ecommerce.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Access rules applied by the gateway (Ant-style path patterns). Every other routed path requires a valid JWT.
 *
 * @param publicPaths     no token needed for any method (register, login)
 * @param publicGetPaths  no token needed for GET/HEAD (browsing the catalog); other methods need a token
 * @param adminPaths      ADMIN role needed for every method
 * @param adminWritePaths ADMIN role needed for anything except GET/HEAD (changing the catalog)
 */
@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(
        List<String> publicPaths,
        List<String> publicGetPaths,
        List<String> adminPaths,
        List<String> adminWritePaths
) {

    public GatewaySecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        publicGetPaths = publicGetPaths == null ? List.of() : List.copyOf(publicGetPaths);
        adminPaths = adminPaths == null ? List.of() : List.copyOf(adminPaths);
        adminWritePaths = adminWritePaths == null ? List.of() : List.copyOf(adminWritePaths);
    }
}
