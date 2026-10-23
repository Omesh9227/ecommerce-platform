package com.ecommerce.orderservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8085/swagger-ui.html
 * All endpoints need a token: log in, then "Authorize" with the accessToken (without "Bearer ").
 * The /api/v1/orders/admin/** endpoints need an ADMIN token.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Order Service API",
                version = "v1",
                description = "Checkout from the cart, order history and the order lifecycle (admin endpoints under /api/v1/orders/admin)",
                contact = @Contact(name = "E-Commerce Platform Team")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Via api-gateway"),
                @Server(url = "http://localhost:8085", description = "Direct")
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
