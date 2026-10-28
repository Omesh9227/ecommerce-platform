package com.ecommerce.adminservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8093/swagger-ui.html
 * Log in, then "Authorize" with the accessToken (without "Bearer ").
 * Everything needs an ADMIN token.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Admin Service API",
                version = "v1",
                description = "Admin dashboard: sales summary, daily sales (JSON / CSV), top products, order pipeline, low stock, activity feed (ADMIN)",
                contact = @Contact(name = "E-Commerce Platform Team")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Via api-gateway"),
                @Server(url = "http://localhost:8093", description = "Direct")
        },
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
)
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
}
