package com.ecommerce.paymentservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8086/swagger-ui.html
 * All endpoints need a token: log in, then "Authorize" with the accessToken (without "Bearer ").
 * The /api/v1/payments/admin/** endpoints need an ADMIN token.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Payment Service API",
                version = "v1",
                description = "Pay for your orders (simulated gateway), payment status and refunds (admin endpoints under /api/v1/payments/admin)",
                contact = @Contact(name = "E-Commerce Platform Team")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Via api-gateway"),
                @Server(url = "http://localhost:8086", description = "Direct")
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
