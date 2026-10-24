package com.ecommerce.searchservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8091/swagger-ui.html
 * Log in, then "Authorize" with the accessToken (without "Bearer ").
 * Search is public; /api/v1/search/admin/** needs ADMIN.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Search Service API",
                version = "v1",
                description = "Product search: full-text with typo correction, filters with counts, suggestions, popular searches. Public; /api/v1/search/admin needs ADMIN",
                contact = @Contact(name = "E-Commerce Platform Team")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Via api-gateway"),
                @Server(url = "http://localhost:8091", description = "Direct")
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
