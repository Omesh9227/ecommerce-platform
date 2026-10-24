package com.ecommerce.shippingservice.integration;

import com.ecommerce.shippingservice.event.OrderEvent;
import com.ecommerce.shippingservice.support.MutableClock;
import com.ecommerce.shippingservice.support.TestTokens;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context + real PostgreSQL (ecommerce_shipping_test_db) + in-memory Kafka.
 * Order events are sent to Kafka by the tests, exactly as order-service would send them.
 * The clock is a test clock (starts at "now"; tests can set it).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"order-events", "shipment-events"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
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

    protected static final String SHIPMENTS_URL = "/api/v1/shipments";
    protected static final String ADMIN_URL = "/api/v1/shipments/admin";

    protected final UUID customerId = UUID.randomUUID();
    protected final UUID otherCustomerId = UUID.randomUUID();
    protected final UUID adminId = UUID.randomUUID();
    /** One token per user for the whole test (tokens contain the current second). */
    protected final String customer = "Bearer " + TestTokens.customer(customerId);
    protected final String otherCustomer = "Bearer " + TestTokens.customer(otherCustomerId);
    protected final String admin = "Bearer " + TestTokens.admin(adminId);

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    protected MutableClock clock;
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tracking_events, shipment_items, shipments, outbox_events");
        clock.reset();
    }

    // ---------- Order events (as order-service sends them) ----------

    protected static String orderNumber(UUID orderId) {
        return "ORD-" + orderId.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    protected static OrderEvent.Address address(String city, String state, String postalCode) {
        return new OrderEvent.Address("Omesh Kumar", "9876543210", "Flat 12B, Green Residency", "MG Road", city, state,
                postalCode, "IN");
    }

    /** ORDER_CONFIRMED (paid) for a Bengaluru address, 2 products. */
    protected OrderEvent confirmed(UUID orderId, UUID userId) {
        return confirmed(orderId, userId, address("Bengaluru", "Karnataka", "560001"));
    }

    protected OrderEvent confirmed(UUID orderId, UUID userId, OrderEvent.Address address) {
        return new OrderEvent(UUID.randomUUID(), "ORDER_CONFIRMED", Instant.now(), orderId, orderNumber(orderId), userId,
                "omesh@example.com", null,
                List.of(new OrderEvent.Item(UUID.randomUUID(), "PIXEL-8", "Pixel 8", 1),
                        new OrderEvent.Item(UUID.randomUUID(), "CASE-01", "Phone case", 2)),
                address, 3);
    }

    protected OrderEvent cancelled(UUID orderId, UUID userId, String reason) {
        return new OrderEvent(UUID.randomUUID(), "ORDER_CANCELLED", Instant.now(), orderId, orderNumber(orderId), userId,
                "omesh@example.com", reason, List.of(), null, 5);
    }

    protected void sendOrderEvent(OrderEvent event) throws Exception {
        sendRaw(event.orderId().toString(), objectMapper.writeValueAsString(event));
    }

    protected void sendRaw(String key, String json) throws Exception {
        kafkaTemplate.send("order-events", key, json).get();
    }

    /** Sends ORDER_CONFIRMED and waits until its shipment exists; returns the shipment id. */
    protected UUID shipmentFor(UUID orderId, UUID userId) throws Exception {
        sendOrderEvent(confirmed(orderId, userId));
        return awaitShipment(orderId);
    }

    protected UUID awaitShipment(UUID orderId) {
        await().atMost(Duration.ofSeconds(20)).until(() -> count("SELECT COUNT(*) FROM shipments WHERE order_id = ?", orderId) == 1);
        return jdbcTemplate.queryForObject("SELECT id FROM shipments WHERE order_id = ?", UUID.class, orderId);
    }

    protected String statusOf(UUID shipmentId) {
        return jdbcTemplate.queryForObject("SELECT status FROM shipments WHERE id = ?", String.class, shipmentId);
    }

    // ---------- API helpers ----------

    protected ResultActions pack(UUID shipmentId) throws Exception {
        return mockMvc.perform(post(ADMIN_URL + "/" + shipmentId + "/pack").header(HttpHeaders.AUTHORIZATION, admin));
    }

    protected ResultActions ship(UUID shipmentId, String carrier, String trackingNumber) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("carrier", carrier);
        body.put("trackingNumber", trackingNumber);
        body.put("location", "Bengaluru warehouse");
        return postJson(ADMIN_URL + "/" + shipmentId + "/ship", admin, body);
    }

    protected ResultActions scan(UUID shipmentId, String status, String location, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("location", location);
        body.put("note", note);
        return postJson(ADMIN_URL + "/" + shipmentId + "/tracking", admin, body);
    }

    /** Packs and ships with Delhivery; returns the tracking number. */
    protected String packAndShip(UUID shipmentId) throws Exception {
        pack(shipmentId).andExpect(status().isOk());
        return readJson(ship(shipmentId, "Delhivery", null).andExpect(status().isOk()).andReturn())
                .at("/data/trackingNumber").asText();
    }

    protected ResultActions postJson(String url, String auth, Object body) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions getAs(String auth, String url) throws Exception {
        var request = get(url);
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return mockMvc.perform(request);
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    // ---------- Reading Kafka ----------

    /** Reads a topic from the beginning (fresh consumer group) until {@code expected} records with this key arrived. */
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
            await().pollInSameThread().atMost(Duration.ofSeconds(40)).until(() -> {
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
