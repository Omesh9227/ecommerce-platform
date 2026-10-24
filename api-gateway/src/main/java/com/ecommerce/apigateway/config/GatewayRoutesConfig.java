package com.ecommerce.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Route table: which path prefix goes to which service.
 * Each URL is lb://&lt;service-name&gt; by default: the gateway asks Eureka (discovery-server) where the service
 * runs and spreads requests over its instances; no instance running gives a JSON 503.
 * New services are added here as new routes.
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
                               @Value("${services.product-service.url}") String productServiceUrl,
                               @Value("${services.inventory-service.url}") String inventoryServiceUrl,
                               @Value("${services.cart-service.url}") String cartServiceUrl,
                               @Value("${services.order-service.url}") String orderServiceUrl,
                               @Value("${services.payment-service.url}") String paymentServiceUrl,
                               @Value("${services.notification-service.url}") String notificationServiceUrl,
                               @Value("${services.wishlist-service.url}") String wishlistServiceUrl,
                               @Value("${services.review-service.url}") String reviewServiceUrl,
                               @Value("${services.shipping-service.url}") String shippingServiceUrl) {
        return builder.routes()
                .route("user-service", route -> route
                        .path("/api/v1/auth/**", "/api/v1/users/**", "/api/v1/admin/**")
                        .filters(filter -> filter.dedupeResponseHeader(CORS_RESPONSE_HEADERS, "RETAIN_FIRST"))
                        .uri(userServiceUrl))
                .route("product-service", route -> route
                        .path("/api/v1/products/**", "/api/v1/categories/**")
                        .filters(filter -> filter.dedupeResponseHeader(CORS_RESPONSE_HEADERS, "RETAIN_FIRST"))
                        .uri(productServiceUrl))
                .route("inventory-service", route -> route
                        .path("/api/v1/inventory/**")
                        .filters(filter -> filter.dedupeResponseHeader(CORS_RESPONSE_HEADERS, "RETAIN_FIRST"))
                        .uri(inventoryServiceUrl))
                .route("cart-service", route -> route
                        .path("/api/v1/cart/**")
                        .filters(filter -> filter.dedupeResponseHeader(CORS_RESPONSE_HEADERS, "RETAIN_FIRST"))
                        .uri(cartServiceUrl))
                .route("order-service", route -> route
                        .path("/api/v1/orders/**")
                        .filters(filter -> filter.dedupeResponseHeader(CORS_RESPONSE_HEADERS, "RETAIN_FIRST"))
                        .uri(orderServiceUrl))
                .route("payment-service", route -> route
                        .path("/api/v1/payments/**")
                        .filters(filter -> filter.dedupeResponseHeader(CORS_RESPONSE_HEADERS, "RETAIN_FIRST"))
                        .uri(paymentServiceUrl))
                .route("notification-service", route -> route
                        .path("/api/v1/notifications/**")
                        .filters(filter -> filter.dedupeResponseHeader(CORS_RESPONSE_HEADERS, "RETAIN_FIRST"))
                        .uri(notificationServiceUrl))
                .route("wishlist-service", route -> route
                        .path("/api/v1/wishlist/**")
                        .filters(filter -> filter.dedupeResponseHeader(CORS_RESPONSE_HEADERS, "RETAIN_FIRST"))
                        .uri(wishlistServiceUrl))
                .route("review-service", route -> route
                        .path("/api/v1/reviews/**")
                        .filters(filter -> filter.dedupeResponseHeader(CORS_RESPONSE_HEADERS, "RETAIN_FIRST"))
                        .uri(reviewServiceUrl))
                .route("shipping-service", route -> route
                        .path("/api/v1/shipments/**")
                        .filters(filter -> filter.dedupeResponseHeader(CORS_RESPONSE_HEADERS, "RETAIN_FIRST"))
                        .uri(shippingServiceUrl))
                .build();
    }
}
