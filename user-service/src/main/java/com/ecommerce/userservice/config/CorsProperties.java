package com.ecommerce.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Comma-separated origins from CORS_ALLOWED_ORIGINS, e.g. "http://localhost:3000,http://localhost:5173". */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
