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
