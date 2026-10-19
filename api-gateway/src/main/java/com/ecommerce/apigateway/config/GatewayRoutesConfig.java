package com.ecommerce.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Route table: which path prefix goes to which service.
 * New services (product, cart, order, ...) are added here as new routes.
 */
@Configuration
public class GatewayRoutesConfig {

    /**
     * CORS is answered by the gateway. If a downstream service also adds CORS headers,
     * browsers reject the duplicate values, so they are de-duplicated on the way out.
     */
    private static final String CORS_RESPONSE_HEADERS = "Access-Control-Allow-Origin Access-Control-Allow-Credentials";

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
                               @Value("${services.user-service.url}") String userServiceUrl,
                               @Value("${services.product-service.url}") String productServiceUrl) {
        return builder.routes()
                .route("user-service", route -> route
                        .path("/api/v1/auth/**", "/api/v1/users/**", "/api/v1/admin/**")
                        .filters(filter -> filter.dedupeResponseHeader(CORS_RESPONSE_HEADERS, "RETAIN_FIRST"))
                        .uri(userServiceUrl))
                .route("product-service", route -> route
                        .path("/api/v1/products/**", "/api/v1/categories/**")
                        .filters(filter -> filter.dedupeResponseHeader(CORS_RESPONSE_HEADERS, "RETAIN_FIRST"))
                        .uri(productServiceUrl))
                .build();
    }
}
