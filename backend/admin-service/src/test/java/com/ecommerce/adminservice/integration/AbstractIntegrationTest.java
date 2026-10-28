package com.ecommerce.adminservice.integration;

import com.ecommerce.adminservice.event.InventoryEvent;
import com.ecommerce.adminservice.event.OrderEvent;
import com.ecommerce.adminservice.event.PaymentEvent;
import com.ecommerce.adminservice.event.ShipmentEvent;
import com.ecommerce.adminservice.ingest.EventRecorder;
import com.ecommerce.adminservice.support.MutableClock;
import com.ecommerce.adminservice.support.TestTokens;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context + real PostgreSQL (ecommerce_admin_test_db) + in-memory Kafka.
 * Most tests feed events through EventRecorder directly (the same code the listeners call); EventsIT covers Kafka.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"order-events", "payment-events", "inventory-events", "shipment-events"},
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

    protected static final String URL = "/api/v1/dashboard";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected EventRecorder recorder;
    @Autowired
    protected KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    protected MutableClock clock;
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    protected final String admin = "Bearer " + TestTokens.admin(UUID.randomUUID());

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE order_items, orders, payment_events, stock_levels, delivery_failures, activity");
        clock.reset();
    }

    // ---------- Events as the other services send them ----------

    /** One order's events; each call returns the next event with a higher orderVersion. */
    protected static final class Order {
        final UUID id = UUID.randomUUID();
        final String number = "ORD-" + id.toString().replace("-", "").substring(0, 12).toUpperCase();
        final UUID userId;
        final BigDecimal total;
        final List<OrderEvent.Item> items;
        long version;

        Order(UUID userId, String total, OrderEvent.Item... items) {
            this.userId = userId;
            this.total = new BigDecimal(total);
            this.items = List.of(items);
        }

        OrderEvent event(String type, String status, String at, String reason) {
            return new OrderEvent(UUID.randomUUID(), type, Instant.parse(at), id, number, userId, status, reason, total, "INR",
                    items, ++version);
        }
    }

    protected static OrderEvent.Item item(UUID productId, String name, int quantity, String unitPrice) {
        return new OrderEvent.Item(productId, name.toUpperCase().replace(' ', '-'), name, quantity, new BigDecimal(unitPrice));
    }

    protected void placed(Order o, String at) {
        recorder.apply(o.event("ORDER_PLACED", "PENDING_PAYMENT", at, null));
    }

    protected void paid(Order o, String at) {
        recorder.apply(new PaymentEvent(UUID.randomUUID(), "PAYMENT_SUCCEEDED", Instant.parse(at), o.id, o.number, o.total, "INR", "UPI", null));
        recorder.apply(o.event("ORDER_CONFIRMED", "CONFIRMED", at, null));
    }

    protected void paymentFailed(Order o, String at) {
        recorder.apply(new PaymentEvent(UUID.randomUUID(), "PAYMENT_FAILED", Instant.parse(at), o.id, o.number, o.total, "INR",
                "CARD", "Your card was declined."));
    }

    protected void shipped(Order o, String at) {
        recorder.apply(o.event("ORDER_SHIPPED", "SHIPPED", at, null));
    }

    protected void delivered(Order o, String at) {
        recorder.apply(o.event("ORDER_DELIVERED", "DELIVERED", at, null));
    }

    protected void cancelled(Order o, String at, String reason) {
        recorder.apply(o.event("ORDER_CANCELLED", "CANCELLED", at, reason));
    }

    protected void refunded(Order o, String at) {
        recorder.apply(new PaymentEvent(UUID.randomUUID(), "PAYMENT_REFUNDED", Instant.parse(at), o.id, o.number, o.total, "INR", "UPI", null));
    }

    protected static InventoryEvent stock(UUID productId, String name, int available, int threshold, String type, long version,
                                          String at) {
        return new InventoryEvent(UUID.randomUUID(), type, Instant.parse(at), productId, name.toUpperCase().replace(' ', '-'), name,
                available + 1, 1, available, threshold, available <= threshold, version);
    }

    protected static ShipmentEvent missedDelivery(Order o, String at) {
        return new ShipmentEvent(UUID.randomUUID(), "SHIPMENT_DELIVERY_FAILED", Instant.parse(at), o.id, o.number, "Delhivery",
                "EC1", "Customer not available", 1);
    }

    // ---------- API ----------

    protected ResultActions getAs(String auth, String path) throws Exception {
        var request = get(URI.create(URL + path));
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return mockMvc.perform(request);
    }

    protected JsonNode data(String path) throws Exception {
        return objectMapper.readTree(getAs(admin, path).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString()).get("data");
    }

    protected int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    protected String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected void awaitCount(String sql, int expected, Object... args) {
        await().atMost(Duration.ofSeconds(20)).until(() -> count(sql, args) == expected);
    }

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
