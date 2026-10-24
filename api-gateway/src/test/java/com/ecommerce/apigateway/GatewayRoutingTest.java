package com.ecommerce.apigateway;

import com.ecommerce.apigateway.support.StubDownstreamServer;
import com.ecommerce.apigateway.support.TestTokens;
import com.sun.net.httpserver.HttpServer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.UUID;

/**
 * End-to-end gateway behaviour: real gateway on a random port, stub downstream service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRoutingTest {

    private static HttpServer downstream;

    @DynamicPropertySource
    static void downstreamUrl(DynamicPropertyRegistry registry) throws IOException {
        downstream = StubDownstreamServer.start();
        registry.add("services.user-service.url", () -> StubDownstreamServer.baseUrl(downstream));
        // The same stub stands in for product-service; it echoes the path, so tests can tell which route was used
        registry.add("services.product-service.url", () -> StubDownstreamServer.baseUrl(downstream));
        registry.add("services.inventory-service.url", () -> StubDownstreamServer.baseUrl(downstream));
        registry.add("services.cart-service.url", () -> StubDownstreamServer.baseUrl(downstream));
        registry.add("services.order-service.url", () -> StubDownstreamServer.baseUrl(downstream));
        registry.add("services.payment-service.url", () -> StubDownstreamServer.baseUrl(downstream));
        registry.add("services.notification-service.url", () -> StubDownstreamServer.baseUrl(downstream));
        registry.add("services.wishlist-service.url", () -> StubDownstreamServer.baseUrl(downstream));
        registry.add("services.review-service.url", () -> StubDownstreamServer.baseUrl(downstream));
        registry.add("services.shipping-service.url", () -> StubDownstreamServer.baseUrl(downstream));
        registry.add("services.search-service.url", () -> StubDownstreamServer.baseUrl(downstream));
        registry.add("services.recommendation-service.url", () -> StubDownstreamServer.baseUrl(downstream));
        registry.add("services.admin-service.url", () -> StubDownstreamServer.baseUrl(downstream));
    }

    @AfterAll
    static void stopDownstream() {
        if (downstream != null) {
            downstream.stop(0);
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    private final UUID customerId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private String customerToken() {
        return TestTokens.valid(customerId, "omesh@example.com", "CUSTOMER");
    }

    private String adminToken() {
        return TestTokens.valid(adminId, "admin@ecommerce.com", "ADMIN");
    }

    // ---------- Public paths ----------

    @Test
    void publicLogin_isForwardedWithoutToken() {
        webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"omesh@example.com\",\"password\":\"Password@123\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/auth/login")
                .jsonPath("$.method").isEqualTo("POST")
                .jsonPath("$.userId").doesNotExist();
    }

    @Test
    void publicPath_spoofedIdentityHeadersAreStripped() {
        webTestClient.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", "hacker")
                .header("X-User-Role", "ADMIN")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").doesNotExist()
                .jsonPath("$.role").doesNotExist();
    }

    @Test
    void actuatorHealth_isPublic() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    // ---------- Authentication (401) ----------

    @Test
    void protectedPath_withoutToken_returns401() {
        webTestClient.get().uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo("Authentication is required to access this resource")
                .jsonPath("$.path").isEqualTo("/api/v1/users/me");
    }

    @Test
    void protectedPath_withMalformedToken_returns401() {
        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Invalid access token");
    }

    @Test
    void protectedPath_withExpiredToken_returns401() {
        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.expired(customerId, "CUSTOMER"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Access token has expired");
    }

    @Test
    void protectedPath_withTokenSignedByAnotherKey_returns401() {
        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.signedWithOtherKey(customerId, "CUSTOMER"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Invalid access token");
    }

    @Test
    void protectedPath_withTokenFromAnotherIssuer_returns401() {
        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.withIssuer("someone-else", customerId, "CUSTOMER"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Invalid access token");
    }

    // ---------- Authenticated forwarding ----------

    @Test
    void validCustomerToken_isForwardedWithIdentityHeaders() {
        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/users/me")
                .jsonPath("$.userId").isEqualTo(customerId.toString())
                .jsonPath("$.userEmail").isEqualTo("omesh@example.com")
                .jsonPath("$.role").isEqualTo("CUSTOMER")
                .jsonPath("$.authorizationPresent").isEqualTo(true);
    }

    @Test
    void spoofedIdentityHeaders_areReplacedWithTokenValues() {
        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .header("X-User-Id", "someone-else")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(customerId.toString())
                .jsonPath("$.role").isEqualTo("CUSTOMER");
    }

    // ---------- Authorization (403) ----------

    @Test
    void customerToken_onAdminPath_returns403() {
        webTestClient.get().uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("FORBIDDEN")
                .jsonPath("$.message").isEqualTo("You do not have permission to access this resource");
    }

    @Test
    void customerToken_withSpoofedAdminHeader_onAdminPath_returns403() {
        webTestClient.get().uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void adminToken_onAdminPath_isForwarded() {
        webTestClient.put().uri("/api/v1/admin/users/" + customerId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"SUSPENDED\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/admin/users/" + customerId + "/status")
                .jsonPath("$.method").isEqualTo("PUT")
                .jsonPath("$.role").isEqualTo("ADMIN");
    }

    @Test
    void pathTraversalAttempt_neverReachesAdminRouteWithCustomerToken() {
        // 400 if the raw ".." path reaches the gateway, 403 if anything normalises it to /api/v1/admin/users first.
        // Either way the customer must not get through.
        webTestClient.get().uri("/api/v1/users/../admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().value(Matchers.oneOf(400, 403));
    }

    // ---------- Product catalog (product-service) ----------

    @Test
    void browsingProducts_isPublic() {
        webTestClient.get().uri("/api/v1/products?search=iphone")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/products")
                .jsonPath("$.userId").doesNotExist();
    }

    @Test
    void readingReviews_isPublic() {
        webTestClient.get().uri("/api/v1/reviews/products/" + UUID.randomUUID() + "/summary")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.method").isEqualTo("GET")
                .jsonPath("$.userId").doesNotExist();
    }

    @Test
    void writingAReview_needsAToken() {
        webTestClient.post().uri("/api/v1/reviews/products/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"rating\":5,\"title\":\"Nice\"}")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.post().uri("/api/v1/reviews/products/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"rating\":5,\"title\":\"Nice\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(customerId.toString());
    }

    @Test
    void reviewModeration_asCustomer_returns403AtTheGateway() {
        webTestClient.get().uri("/api/v1/reviews/admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void trackingAParcel_andDeliveryEstimates_arePublic() {
        webTestClient.get().uri("/api/v1/shipments/track/EC482019375610")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/shipments/track/EC482019375610")
                .jsonPath("$.userId").doesNotExist();
        webTestClient.get().uri("/api/v1/shipments/delivery-estimate?postalCode=110001")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void myShipments_needAToken_andWarehouseActionsNeedAdmin() {
        webTestClient.get().uri("/api/v1/shipments").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/v1/shipments/orders/ORD-3FA85F645717")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.userId").isEqualTo(customerId.toString());
        webTestClient.post().uri("/api/v1/shipments/admin/" + UUID.randomUUID() + "/pack")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isForbidden();
        webTestClient.post().uri("/api/v1/shipments/track/EC482019375610")
                .exchange()
                .expectStatus().isUnauthorized(); // only GET is public
    }

    @Test
    void search_isPublic_butItsAdminApiIsNot() {
        webTestClient.get().uri("/api/v1/search/products?q=pixel&brand=Google")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/search/products")
                .jsonPath("$.userId").doesNotExist();
        webTestClient.get().uri("/api/v1/search/suggest?q=pix").exchange().expectStatus().isOk();
        webTestClient.get().uri("/api/v1/search/popular").exchange().expectStatus().isOk();
        webTestClient.get().uri("/api/v1/search/admin/stats").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/v1/search/admin/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isForbidden();
        webTestClient.get().uri("/api/v1/search/admin/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void productPageRecommendations_arePublic_personalOnesNeedAToken() {
        webTestClient.get().uri("/api/v1/recommendations/products/" + UUID.randomUUID() + "/bought-together")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.userId").doesNotExist();
        webTestClient.get().uri("/api/v1/recommendations/trending").exchange().expectStatus().isOk();
        webTestClient.get().uri("/api/v1/recommendations/for-you").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/v1/recommendations/for-you")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.userId").isEqualTo(customerId.toString());
        webTestClient.get().uri("/api/v1/recommendations/admin/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void dashboard_isForAdminsOnly() {
        webTestClient.get().uri("/api/v1/dashboard/summary").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/v1/dashboard/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isForbidden();
        webTestClient.get().uri("/api/v1/dashboard/sales/daily.csv?from=2026-10-01")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/api/v1/dashboard/sales/daily.csv");
    }

    @Test
    void browsingCategories_isPublic() {
        webTestClient.get().uri("/api/v1/categories")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/api/v1/categories");
    }

    @Test
    void browsingProducts_withToken_forwardsAuthorizationForTheService() {
        webTestClient.get().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.authorizationPresent").isEqualTo(true)
                // Public path: identity headers are not added (the service validates the token itself)
                .jsonPath("$.role").doesNotExist();
    }

    @Test
    void creatingProduct_withoutToken_returns401() {
        webTestClient.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Authentication is required to access this resource");
    }

    @Test
    void creatingProduct_asCustomer_returns403() {
        webTestClient.post().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void deletingCategory_asCustomer_returns403() {
        webTestClient.delete().uri("/api/v1/categories/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void creatingProduct_asAdmin_isForwardedToProductService() {
        webTestClient.post().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/products")
                .jsonPath("$.method").isEqualTo("POST")
                .jsonPath("$.role").isEqualTo("ADMIN")
                .jsonPath("$.userId").isEqualTo(adminId.toString());
    }

    // ---------- Inventory (inventory-service, ADMIN only) ----------

    @Test
    void inventory_withoutToken_returns401() {
        webTestClient.get().uri("/api/v1/inventory")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void inventory_asCustomer_returns403_evenForGet() {
        webTestClient.get().uri("/api/v1/inventory/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void inventory_asAdmin_isForwardedToInventoryService() {
        webTestClient.get().uri("/api/v1/inventory?lowStock=true")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/inventory")
                .jsonPath("$.role").isEqualTo("ADMIN")
                .jsonPath("$.userId").isEqualTo(adminId.toString());
    }

    @Test
    void reservation_asAdmin_isForwardedToInventoryService() {
        webTestClient.post().uri("/api/v1/inventory/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/inventory/reservations")
                .jsonPath("$.method").isEqualTo("POST");
    }

    // ---------- Cart (cart-service, any logged-in user) ----------

    @Test
    void cart_withoutToken_returns401() {
        webTestClient.get().uri("/api/v1/cart")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void cart_asCustomer_isForwardedWithIdentity() {
        webTestClient.post().uri("/api/v1/cart/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/cart/items")
                .jsonPath("$.method").isEqualTo("POST")
                .jsonPath("$.role").isEqualTo("CUSTOMER")
                .jsonPath("$.userId").isEqualTo(customerId.toString());
    }

    // ---------- Orders (order-service) ----------

    @Test
    void checkout_asCustomer_isForwarded_withTheIdempotencyKey() {
        webTestClient.post().uri("/api/v1/orders/checkout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .header("Idempotency-Key", "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/orders/checkout")
                .jsonPath("$.role").isEqualTo("CUSTOMER")
                .jsonPath("$.authorizationPresent").isEqualTo(true);
    }

    @Test
    void orderAdminEndpoints_asCustomer_return403AtTheGateway() {
        webTestClient.get().uri("/api/v1/orders/admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void orderAdminEndpoints_asAdmin_areForwarded() {
        webTestClient.post().uri("/api/v1/orders/admin/ORD-3FA85F645717/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/orders/admin/ORD-3FA85F645717/cancel")
                .jsonPath("$.role").isEqualTo("ADMIN");
    }

    @Test
    void orders_withoutToken_return401() {
        webTestClient.get().uri("/api/v1/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ---------- Payments (payment-service) ----------

    @Test
    void pay_asCustomer_isForwarded() {
        webTestClient.post().uri("/api/v1/payments/orders/ORD-3FA85F645717/pay")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .header("Idempotency-Key", "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/payments/orders/ORD-3FA85F645717/pay")
                .jsonPath("$.role").isEqualTo("CUSTOMER");
    }

    @Test
    void paymentAdmin_asCustomer_returns403AtTheGateway() {
        webTestClient.get().uri("/api/v1/payments/admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void payments_withoutToken_return401() {
        webTestClient.get().uri("/api/v1/payments/orders/ORD-3FA85F645717")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ---------- Notifications (notification-service) ----------

    @Test
    void myNotifications_asCustomer_areForwarded() {
        webTestClient.get().uri("/api/v1/notifications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/notifications")
                .jsonPath("$.userId").isEqualTo(customerId.toString());
    }

    @Test
    void myWishlist_asCustomer_isForwarded() {
        webTestClient.get().uri("/api/v1/wishlist")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/wishlist")
                .jsonPath("$.userId").isEqualTo(customerId.toString());
    }

    @Test
    void wishlist_withoutToken_returns401AtTheGateway() {
        webTestClient.post().uri("/api/v1/wishlist/items")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"productId\":\"" + UUID.randomUUID() + "\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void notificationAdmin_asCustomer_returns403AtTheGateway() {
        webTestClient.get().uri("/api/v1/notifications/admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .exchange()
                .expectStatus().isForbidden();
    }

    // ---------- Routing / errors ----------

    @Test
    void unknownRoute_returnsJson404() {
        webTestClient.get().uri("/api/v1/does-not-exist")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").isEqualTo("NOT_FOUND")
                .jsonPath("$.message").isEqualTo("Resource not found");
    }

    // ---------- Correlation id ----------

    @Test
    void correlationId_fromClient_isForwardedAndReturned() {
        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken())
                .header("X-Correlation-Id", "test-correlation-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "test-correlation-123")
                .expectBody().jsonPath("$.correlationId").isEqualTo("test-correlation-123");
    }

    @Test
    void correlationId_isGeneratedWhenMissing_evenOnErrors() {
        webTestClient.get().uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Correlation-Id");
    }

    // ---------- CORS ----------

    @Test
    void corsPreflight_fromAllowedOrigin_isAnsweredByGateway() {
        webTestClient.method(HttpMethod.OPTIONS).uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");
    }

    @Test
    void corsPreflight_fromUnknownOrigin_isRejected() {
        webTestClient.method(HttpMethod.OPTIONS).uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isForbidden();
    }
}
