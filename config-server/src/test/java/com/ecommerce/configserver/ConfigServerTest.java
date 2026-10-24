package com.ecommerce.configserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real Config Server on a random port, serving the real config-repo/ folder:
 * what each service will receive at startup, and that it's only given out with the password.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"native", "test"})
class ConfigServerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private TestRestTemplate loggedIn() {
        return restTemplate.withBasicAuth("test-config", "test-config-secret");
    }

    /** The settings a service gets, merged the way the service merges them (first property source wins). */
    private Map<String, String> configFor(String service) throws Exception {
        ResponseEntity<String> response = loggedIn().getForEntity("/" + service + "/default", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, String> merged = new LinkedHashMap<>();
        for (JsonNode propertySource : objectMapper.readTree(response.getBody()).get("propertySources")) {
            for (Map.Entry<String, JsonNode> property : propertySource.get("source").properties()) {
                merged.putIfAbsent(property.getKey(), property.getValue().asText());
            }
        }
        return merged;
    }

    @ParameterizedTest
    @ValueSource(strings = {"api-gateway", "user-service", "product-service", "inventory-service", "cart-service",
            "order-service", "payment-service", "notification-service", "wishlist-service", "review-service",
            "shipping-service", "search-service", "recommendation-service"})
    void everyService_getsTheSharedSettings(String service) throws Exception {
        Map<String, String> config = configFor(service);

        assertThat(config.get("eureka.client.service-url.defaultZone")).contains("/eureka/");
        assertThat(config.get("eureka.instance.instance-id")).isEqualTo("${spring.application.name}:${server.port}");
        assertThat(config).containsEntry("shop.currency", "INR");
        assertThat(config).containsEntry("shop.payment-window-minutes", "30");
    }

    @ParameterizedTest
    @CsvSource({
            "product-service,      app.catalog.currency,                     ${shop.currency}",
            "inventory-service,    app.inventory.default-low-stock-threshold, 5",
            "cart-service,         app.cart.max-quantity-per-item,           10",
            "cart-service,         app.cart.currency,                        ${shop.currency}",
            "order-service,        app.order.shipping-fee,                   40.00",
            "order-service,        app.order.free-shipping-threshold,        499.00",
            "order-service,        app.order.payment-window,                 ${shop.payment-window-minutes}m",
            "payment-service,      app.payment.gateway,                      simulated",
            "notification-service, app.notification.from-address,            no-reply@ecommerce.local",
            "notification-service, app.notification.payment-window-minutes,  ${shop.payment-window-minutes}",
            "wishlist-service,     app.wishlist.max-items,                   100",
            "wishlist-service,     app.wishlist.price-drop-min-percent,      5",
            "wishlist-service,     app.wishlist.currency,                    ${shop.currency}",
            "review-service,       app.review.verified-purchase-required,    false",
            "shipping-service,     app.shipping.origin-postal-code,          560001",
            "shipping-service,     app.shipping.time-zone,                   Asia/Kolkata",
            "shipping-service,     app.shipping.delivery-days.other,         5",
            "search-service,       app.search.price-ranges,                  '500,1000,5000,20000'",
            "search-service,       app.search.currency,                      ${shop.currency}",
            "recommendation-service, app.recommendation.trending-days,       7",
            "recommendation-service, app.recommendation.currency,            ${shop.currency}"
    })
    void eachService_getsItsOwnSettings(String service, String key, String value) throws Exception {
        assertThat(configFor(service)).containsEntry(key, value);
    }

    @Test
    void settingsOfOneService_areNotGivenToAnother() throws Exception {
        Map<String, String> config = configFor("user-service");

        assertThat(config).doesNotContainKeys("app.order.shipping-fee", "app.cart.max-items", "app.payment.gateway");
    }

    @Test
    void withoutLogin_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/order-service/default", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void withWrongPassword_returns401() {
        ResponseEntity<String> response = restTemplate.withBasicAuth("test-config", "wrong")
                .getForEntity("/order-service/default", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void health_isPublic() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }
}
