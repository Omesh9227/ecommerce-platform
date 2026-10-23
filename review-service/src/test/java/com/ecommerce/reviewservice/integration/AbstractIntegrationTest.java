package com.ecommerce.reviewservice.integration;

import com.ecommerce.reviewservice.event.OrderEvent;
import com.ecommerce.reviewservice.support.StubServices;
import com.ecommerce.reviewservice.support.TestTokens;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context + real PostgreSQL (ecommerce_review_test_db) + in-memory Kafka.
 * product-service and user-service are a stub server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"order-events", "review-events"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public abstract class AbstractIntegrationTest {

    protected static final String REVIEWS_URL = "/api/v1/reviews";
    protected static final String ADMIN_URL = "/api/v1/reviews/admin";

    protected static final StubServices stub = StubServices.start();

    protected final UUID customerId = UUID.randomUUID();
    protected final UUID otherCustomerId = UUID.randomUUID();
    protected final UUID adminId = UUID.randomUUID();
    /** One token per customer for the whole test (tokens contain the current second). */
    protected final String customer = "Bearer " + TestTokens.customer(customerId);
    protected final String otherCustomer = "Bearer " + TestTokens.customer(otherCustomerId);
    protected final String admin = "Bearer " + TestTokens.admin(adminId);

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("services.product-service.url", stub::baseUrl);
        registry.add("services.user-service.url", stub::baseUrl);
    }

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE review_votes, reviews, purchases, product_ratings, outbox_events");
        stub.reset();
    }

    protected static String newCustomerToken() {
        return "Bearer " + TestTokens.customer(UUID.randomUUID());
    }

    // ---------- API helpers ----------

    protected ResultActions review(String auth, UUID productId, int rating, String title, String comment) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("rating", rating);
        body.put("title", title);
        body.put("comment", comment);
        return mockMvc.perform(post(REVIEWS_URL + "/products/" + productId).header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    /** Writes a review and returns its id. */
    protected UUID reviewId(String auth, UUID productId, int rating) throws Exception {
        MvcResult result = review(auth, productId, rating, rating + " stars", "Comment").andExpect(status().isCreated()).andReturn();
        return UUID.fromString(readJson(result).at("/data/id").asText());
    }

    /** The "data" of GET /api/v1/reviews/products/{id}/summary (public, no token). */
    protected JsonNode summary(UUID productId) throws Exception {
        return readJson(mockMvc.perform(get(REVIEWS_URL + "/products/" + productId + "/summary"))
                .andExpect(status().isOk()).andReturn()).get("data");
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

    // ---------- Events ----------

    /** ORDER_DELIVERED as order-service sends it. */
    protected static OrderEvent delivered(UUID userId, UUID... productIds) {
        List<OrderEvent.Item> items = new ArrayList<>();
        for (UUID productId : productIds) {
            items.add(new OrderEvent.Item(productId, "SKU", "Product", 1, new BigDecimal("1000")));
        }
        return new OrderEvent(UUID.randomUUID(), "ORDER_DELIVERED", Instant.parse("2026-10-20T10:00:00Z"),
                UUID.randomUUID(), "ORD-0000000000AB", userId, items);
    }

    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
