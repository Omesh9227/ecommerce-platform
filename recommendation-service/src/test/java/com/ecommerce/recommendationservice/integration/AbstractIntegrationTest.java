package com.ecommerce.recommendationservice.integration;

import com.ecommerce.recommendationservice.event.CategoryEvent;
import com.ecommerce.recommendationservice.event.InventoryEvent;
import com.ecommerce.recommendationservice.event.ProductEvent;
import com.ecommerce.recommendationservice.event.ReviewEvent;
import com.ecommerce.recommendationservice.catalog.CatalogIndexer;
import com.ecommerce.recommendationservice.event.OrderEvent;
import com.ecommerce.recommendationservice.orders.PurchaseHistory;
import com.ecommerce.recommendationservice.support.MutableClock;
import com.ecommerce.recommendationservice.support.TestTokens;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context + real PostgreSQL (ecommerce_recommendation_test_db) + in-memory Kafka.
 * Most tests feed events through CatalogIndexer / PurchaseHistory directly (the same code the Kafka listeners call);
 * the Kafka path itself is tested in EventsIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"product-events", "category-events", "inventory-events", "review-events", "order-events"},
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

    protected static final String URL = "/api/v1/recommendations";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected CatalogIndexer indexer;
    @Autowired
    protected PurchaseHistory purchases;
    @Autowired
    protected KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    protected MutableClock clock;
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @BeforeEach
    void cleanIndex() {
        jdbcTemplate.execute("TRUNCATE TABLE products, categories, order_items, orders, bought_together, daily_sales, product_views, viewed_together");
        clock.reset();
    }

    // ---------- Events as the other services send them ----------

    protected static ProductEvent product(UUID id, String sku, String name, String brand, UUID categoryId, String price,
                                          String discount, String description, String status, long version, Instant createdAt) {
        BigDecimal list = new BigDecimal(price);
        BigDecimal off = new BigDecimal(discount == null ? "0" : discount);
        BigDecimal selling = list.multiply(BigDecimal.valueOf(100).subtract(off)).divide(BigDecimal.valueOf(100), 2,
                java.math.RoundingMode.HALF_UP);
        return new ProductEvent(UUID.randomUUID(), version == 0 ? "PRODUCT_CREATED" : "PRODUCT_UPDATED", Instant.now(), id, sku,
                name, status, list, selling, brand, description, categoryId, off,
                List.of("https://cdn.example.com/" + sku.toLowerCase() + ".jpg"), createdAt, version);
    }

    protected static CategoryEvent category(String type, UUID id, String name, UUID parentId, long version) {
        return new CategoryEvent(UUID.randomUUID(), type, id, name, parentId, version);
    }

    protected static InventoryEvent stock(UUID productId, int available, long version) {
        return new InventoryEvent(UUID.randomUUID(), "INVENTORY_UPDATED", productId, available, version);
    }

    protected static ReviewEvent rating(UUID productId, String average, int count, long version) {
        return new ReviewEvent(UUID.randomUUID(), "PRODUCT_RATING_UPDATED", productId, new BigDecimal(average), count, version);
    }

    /** ORDER_CONFIRMED (paid) with these products, one of each. */
    protected static OrderEvent paid(UUID orderId, UUID userId, UUID... productIds) {
        List<OrderEvent.Item> items = new ArrayList<>();
        for (UUID p : productIds) {
            items.add(new OrderEvent.Item(p, 1));
        }
        return new OrderEvent(UUID.randomUUID(), "ORDER_CONFIRMED", Instant.now(), orderId,
                "ORD-" + orderId.toString().substring(0, 12).toUpperCase(), userId, items);
    }

    protected static OrderEvent cancelled(UUID orderId, UUID userId) {
        return new OrderEvent(UUID.randomUUID(), "ORDER_CANCELLED", Instant.now(), orderId,
                "ORD-" + orderId.toString().substring(0, 12).toUpperCase(), userId, List.of());
    }

    // ---------- API ----------

    /** The "data" of a successful GET. */
    protected JsonNode data(String auth, String path) throws Exception {
        return objectMapper.readTree(getAs(auth, URL + path).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString()).get("data");
    }

    /** Product names of a recommendation list. */
    protected List<String> names(JsonNode list) {
        List<String> names = new ArrayList<>();
        list.get("products").forEach(p -> names.add(p.get("name").asText()));
        return names;
    }

    protected List<String> reasons(JsonNode list) {
        List<String> reasons = new ArrayList<>();
        list.get("products").forEach(p -> reasons.add(p.get("reason").asText()));
        return reasons;
    }

    protected ResultActions view(String auth, UUID productId) throws Exception {
        var request = post(URI.create(URL + "/views")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + productId + "\"}");
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return mockMvc.perform(request);
    }

    protected static String customerAuth(UUID userId) {
        return "Bearer " + TestTokens.customer(userId);
    }

    protected ResultActions getAs(String auth, String url) throws Exception {
        var request = get(URI.create(url));
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return mockMvc.perform(request);
    }

    protected static String adminAuth() {
        return "Bearer " + TestTokens.admin(UUID.randomUUID());
    }

    protected static String customerAuth() {
        return "Bearer " + TestTokens.customer(UUID.randomUUID());
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
