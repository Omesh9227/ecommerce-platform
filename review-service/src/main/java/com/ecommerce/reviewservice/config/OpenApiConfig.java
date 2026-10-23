package com.ecommerce.reviewservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8089/swagger-ui.html
 * All endpoints need a token: log in, then "Authorize" with the accessToken (without "Bearer ").
 * Reading reviews is public; writing needs a token; /api/v1/reviews/admin/** needs ADMIN.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Review Service API",
                version = "v1",
                description = "Product reviews: ratings 1-5, verified purchases, helpful votes, rating summaries. Reading is public, writing needs a token, /api/v1/reviews/admin needs ADMIN",
                contact = @Contact(name = "E-Commerce Platform Team")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Via api-gateway"),
                @Server(url = "http://localhost:8089", description = "Direct")
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
