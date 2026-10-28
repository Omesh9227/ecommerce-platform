package com.ecommerce.shippingservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8090/swagger-ui.html
 * Log in, then "Authorize" with the accessToken (without "Bearer ").
 * Tracking by tracking number and delivery estimates are public; /api/v1/shipments/admin/** needs ADMIN.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Shipping Service API",
                version = "v1",
                description = "Shipments for paid orders: packing, dispatch, carrier tracking, delivery estimates. Tracking and estimates are public, /api/v1/shipments/admin needs ADMIN",
                contact = @Contact(name = "E-Commerce Platform Team")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Via api-gateway"),
                @Server(url = "http://localhost:8090", description = "Direct")
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
