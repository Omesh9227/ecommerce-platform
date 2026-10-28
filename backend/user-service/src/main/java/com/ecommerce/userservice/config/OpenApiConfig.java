package com.ecommerce.userservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8081/swagger-ui.html
 * Use "Authorize" with the accessToken from /api/v1/auth/login (paste the token only, without "Bearer ").
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "User Service API",
                version = "v1",
                description = "Registration, login (JWT), profile and admin user management for the e-commerce platform",
                contact = @Contact(name = "E-Commerce Platform Team")
        ),
        servers = @Server(url = "http://localhost:8081", description = "Local")
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
