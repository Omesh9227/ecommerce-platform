package com.ecommerce.orderservice.integration;

import com.ecommerce.orderservice.event.EventTypes;
import com.ecommerce.orderservice.event.PaymentEvent;
import com.ecommerce.orderservice.event.ShipmentEvent;
import com.ecommerce.orderservice.service.OrderLifecycleService;
import com.ecommerce.orderservice.service.PaymentEventHandler;
import com.ecommerce.orderservice.support.MutableClock;
import com.ecommerce.orderservice.support.StubServices;
import com.ecommerce.orderservice.support.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context + real PostgreSQL (ecommerce_order_test_db) + in-memory Kafka + a stub for
 * cart-, product- and inventory-service + a clock the tests can move forward.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"order-events", "payment-events", "shipment-events"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Import(AbstractIntegrationTest.TestClockConfig.class)
public abstract class AbstractIntegrationTest {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    protected static final String ORDERS_URL = "/api/v1/orders";
    protected static final String CHECKOUT_URL = "/api/v1/orders/checkout";
    protected static final String ADMIN_URL = "/api/v1/orders/admin";

    /** Shared by all test classes in this JVM, like the cached Spring context. */
    protected static final StubServices stub = StubServices.start();

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("services.cart-service.url", stub::baseUrl);
        registry.add("services.product-service.url", stub::baseUrl);
        registry.add("services.inventory-service.url", stub::baseUrl);
    }

    protected final UUID customerId = UUID.randomUUID();
    protected final UUID otherCustomerId = UUID.randomUUID();
    protected final UUID adminId = UUID.randomUUID();

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected PaymentEventHandler paymentEventHandler;
    @Autowired
    protected OrderLifecycleService lifecycleService;
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // Tokens are created once per test so the same Authorization header identifies the same cart in the stub
    protected String customerAuth;
    protected String otherCustomerAuth;
    protected String adminAuth;

    @BeforeEach
    void cleanState() {
        jdbcTemplate.execute("TRUNCATE TABLE order_status_history, order_items, orders, outbox_events");
        stub.reset();
        clock.reset();
        customerAuth = "Bearer " + TestTokens.customer(customerId);
        otherCustomerAuth = "Bearer " + TestTokens.customer(otherCustomerId);
        adminAuth = "Bearer " + TestTokens.admin(adminId);
    }

    protected static String newKey() {
        return UUID.randomUUID().toString();
    }

    protected static String addressJson() {
        return """
                {"fullName":"Omesh Kumar","phone":"9876543210","line1":"Flat 12B, Green Residency","line2":"MG Road",
                 "city":"Bengaluru","state":"Karnataka","postalCode":"560001"}""";
    }

    protected static String checkoutJson() {
        return "{\"shippingAddress\":" + addressJson() + "}";
    }

    protected ResultActions checkout(String auth, String idempotencyKey, String body) throws Exception {
        var request = post(CHECKOUT_URL).header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    /** Puts one product in the customer's cart and checks out; returns the order number. */
    protected String placeOrder(String auth, UUID productId, int quantity) throws Exception {
        stub.addToCart(auth, productId, quantity);
        MvcResult result = checkout(auth, newKey(), checkoutJson()).andExpect(status().isCreated()).andReturn();
        return readJson(result).at("/data/orderNumber").asText();
    }

    protected UUID orderId(String orderNumber) {
        return UUID.fromString(jdbcTemplate.queryForObject("SELECT id::text FROM orders WHERE order_number = ?",
                String.class, orderNumber));
    }

    /** A PAYMENT_SUCCEEDED / PAYMENT_REFUNDED / PAYMENT_FAILED event as payment-service would publish it. */
    protected PaymentEvent paymentEvent(String type, String orderNumber) {
        return new PaymentEvent(UUID.randomUUID(), type, orderId(orderNumber), orderNumber,
                new java.math.BigDecimal("1800.00"), "INR", "CARD", "VISA", "4242", null);
    }

    /** The order is paid (as if payment-service had sent PAYMENT_SUCCEEDED). */
    protected void pay(String orderNumber) {
        paymentEventHandler.handle(paymentEvent(EventTypes.PAYMENT_SUCCEEDED, orderNumber));
    }

    /** A shipment event as shipping-service would publish it. */
    protected ShipmentEvent shipmentEvent(String type, String orderNumber, long shipmentVersion, String note) {
        return new ShipmentEvent(UUID.randomUUID(), type, orderId(orderNumber), orderNumber, "Delhivery", "EC482019375610",
                java.time.LocalDate.of(2026, 10, 27), "Koramangala", note, shipmentVersion);
    }

    /** shipping-service reports a step (as if its event had arrived). */
    protected void shipment(String type, String orderNumber, long shipmentVersion) {
        lifecycleService.applyShipmentEvent(shipmentEvent(type, orderNumber, shipmentVersion, null));
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected String orderStatus(String orderNumber) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_number = ?", String.class, orderNumber);
    }

    /** Reads order-events from the beginning (fresh consumer group) until {@code expected} events for this key arrived. */
    protected List<ConsumerRecord<String, String>> awaitEvents(String key, int expected) {
        return awaitRecords("order-events", key, expected);
    }

    /** Reads a topic from the beginning (fresh consumer group) until {@code expected} records for this key arrived. */
    protected List<ConsumerRecord<String, String>> awaitRecords(String topic, String key, int expected) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            await().pollInSameThread().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(300))) {
                    if (key.equals(record.key())) {
                        found.add(record);
                    }
                }
                return found.size() >= expected;
            });
        }
        return found;
    }
}
