package com.ecommerce.cartservice.client;

import com.ecommerce.cartservice.support.StubProductService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestProductCatalogClientTest {

    private static StubProductService stub;

    @BeforeAll
    static void startStub() {
        stub = StubProductService.start();
    }

    @AfterAll
    static void stopStub() {
        stub.stop();
    }

    @BeforeEach
    void resetStub() {
        stub.reset();
    }

    private static RestProductCatalogClient clientFor(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(2));
        return new RestProductCatalogClient(RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build());
    }

    @Test
    void returnsActiveProducts_inOneRequest_andLeavesOutUnknownIds() {
        UUID phone = stub.put("PHONE", "100.00", 5);
        UUID phoneCase = stub.put("CASE", "10.00", 0);
        UUID unknown = UUID.randomUUID();

        Map<UUID, CatalogProduct> found = clientFor(stub.baseUrl()).findActiveProducts(List.of(phone, phoneCase, unknown, phone));

        assertThat(found).containsOnlyKeys(phone, phoneCase);
        assertThat(found.get(phone).sellingPrice()).isEqualByComparingTo("100.00");
        assertThat(found.get(phone).availableQuantity()).isEqualTo(5);
        assertThat(found.get(phone).firstImageUrl()).isEqualTo("https://cdn.example.com/phone.jpg");
        assertThat(found.get(phoneCase).inStock()).isFalse();
        assertThat(stub.lookups()).isEqualTo(1);
        assertThat(stub.lastQuery()).contains("ids=").contains(phone.toString()).contains(unknown.toString());
    }

    @Test
    void noIds_noRequest() {
        assertThat(clientFor(stub.baseUrl()).findActiveProducts(List.of())).isEmpty();
        assertThat(stub.lookups()).isZero();
    }

    @Test
    void serverError_isCatalogUnavailable() {
        stub.setDown(true);

        assertThatThrownBy(() -> clientFor(stub.baseUrl()).findActiveProducts(List.of(UUID.randomUUID())))
                .isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void connectionRefused_isCatalogUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> clientFor("http://localhost:" + closedPort).findActiveProducts(List.of(UUID.randomUUID())))
                .isInstanceOf(CatalogUnavailableException.class);
    }
}
