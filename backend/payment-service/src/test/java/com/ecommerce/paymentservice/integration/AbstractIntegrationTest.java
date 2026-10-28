package com.ecommerce.paymentservice.integration;

import com.ecommerce.paymentservice.event.EventTypes;
import com.ecommerce.paymentservice.event.OrderEvent;
import com.ecommerce.paymentservice.service.OrderEventHandler;
import com.ecommerce.paymentservice.support.TestTokens;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Full application context + real PostgreSQL (ecommerce_payment_test_db) + in-memory Kafka. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"order-events", "payment-events"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public abstract class AbstractIntegrationTest {

    protected static final String PAYMENTS_URL = "/api/v1/payments/orders";
    protected static final String ADMIN_URL = "/api/v1/payments/admin";

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
    protected OrderEventHandler orderEventHandler;
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE payment_attempts, payments, outbox_events");
    }

    protected String customerAuth() {
        return "Bearer " + TestTokens.customer(customerId);
    }

    protected String otherCustomerAuth() {
        return "Bearer " + TestTokens.customer(otherCustomerId);
    }

    protected String adminAuth() {
        return "Bearer " + TestTokens.admin(adminId);
    }

    protected static String newKey() {
        return UUID.randomUUID().toString();
    }

    protected OrderEvent orderEvent(String type, UUID orderId, String orderNumber, UUID userId, String total) {
        return new OrderEvent(UUID.randomUUID(), type, orderId, orderNumber, userId,
                total == null ? null : new BigDecimal(total), "INR", type.equals(EventTypes.ORDER_CANCELLED) ? "Changed my mind" : null, 1);
    }

    /** As if order-service had published ORDER_PLACED for this customer. @return the order id */
    protected UUID orderPlaced(String orderNumber, String total) {
        UUID orderId = UUID.randomUUID();
        orderEventHandler.handle(orderEvent(EventTypes.ORDER_PLACED, orderId, orderNumber, customerId, total));
        return orderId;
    }

    protected void orderCancelled(UUID orderId, String orderNumber) {
        orderEventHandler.handle(orderEvent(EventTypes.ORDER_CANCELLED, orderId, orderNumber, customerId, null));
    }

    protected static String cardJson(String number) {
        return "{\"method\":\"CARD\",\"card\":{\"number\":\"" + number
                + "\",\"expiryMonth\":12,\"expiryYear\":2030,\"cvv\":\"123\",\"holderName\":\"Omesh Kumar\"}}";
    }

    protected static String upiJson(String upiId) {
        return "{\"method\":\"UPI\",\"upiId\":\"" + upiId + "\"}";
    }

    protected ResultActions pay(String auth, String orderNumber, String key, String body) throws Exception {
        var request = post(PAYMENTS_URL + "/" + orderNumber + "/pay").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected String paymentStatus(String orderNumber) {
        return jdbcTemplate.queryForObject("SELECT status FROM payments WHERE order_number = ?", String.class, orderNumber);
    }

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

    protected String eventType(String json) {
        try {
            return objectMapper.readTree(json).get("eventType").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
