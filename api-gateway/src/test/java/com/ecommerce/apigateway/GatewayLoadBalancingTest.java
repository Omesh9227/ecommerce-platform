package com.ecommerce.apigateway;

import com.ecommerce.apigateway.support.StubDownstreamServer;
import com.ecommerce.apigateway.support.TestTokens;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.UUID;

/**
 * Routes with lb://service-name URLs, as in production. Instead of Eureka, a fixed list of instances
 * (Spring Cloud's simple discovery client) says where order-service runs; cart-service has no instance at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayLoadBalancingTest {

    private static HttpServer orderServiceInstance;

    @DynamicPropertySource
    static void serviceRegistry(DynamicPropertyRegistry registry) throws IOException {
        orderServiceInstance = StubDownstreamServer.start();
        registry.add("services.order-service.url", () -> "lb://order-service");
        registry.add("spring.cloud.discovery.client.simple.instances.order-service[0].uri",
                () -> StubDownstreamServer.baseUrl(orderServiceInstance));
        registry.add("services.cart-service.url", () -> "lb://cart-service");
    }

    @AfterAll
    static void stopInstance() {
        if (orderServiceInstance != null) {
            orderServiceInstance.stop(0);
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    private final UUID customerId = UUID.randomUUID();

    private String customerToken() {
        return TestTokens.valid(customerId, "omesh@example.com", "CUSTOMER");
    }

    @Test
    void lbRoute_isSentToARegisteredInstance() {
        webTestClient.get().uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/orders")
                .jsonPath("$.userId").isEqualTo(customerId.toString());
    }

    @Test
    void serviceWithNoRunningInstance_returnsJson503() {
        webTestClient.get().uri("/api/v1/cart")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.message").isEqualTo("Service temporarily unavailable. Please try again later.")
                .jsonPath("$.path").isEqualTo("/api/v1/cart");
    }
}
