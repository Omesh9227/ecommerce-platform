package com.ecommerce.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.ServerSocket;

/** When user-service is not running, the gateway must answer with a clean JSON 503, not a stack trace. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayDownstreamUnavailableTest {

    @DynamicPropertySource
    static void unreachableDownstream(DynamicPropertyRegistry registry) throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        registry.add("services.user-service.url", () -> "http://localhost:" + closedPort);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void downstreamDown_returnsJson503() {
        webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"omesh@example.com\",\"password\":\"Password@123\"}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.message").isEqualTo("Service temporarily unavailable. Please try again later.")
                .jsonPath("$.path").isEqualTo("/api/v1/auth/login");
    }
}
