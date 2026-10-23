package com.ecommerce.wishlistservice.integration;

import com.ecommerce.wishlistservice.event.EventTypes;
import com.ecommerce.wishlistservice.event.ProductEvent;
import com.ecommerce.wishlistservice.support.StubServices;
import com.ecommerce.wishlistservice.support.TestTokens;
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
 * Full application context + real PostgreSQL (ecommerce_wishlist_test_db) + in-memory Kafka.
 * product-service and cart-service are a stub server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"product-events", "wishlist-events"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public abstract class AbstractIntegrationTest {

    protected static final String WISHLIST_URL = "/api/v1/wishlist";
    protected static final String ITEMS_URL = "/api/v1/wishlist/items";
    /** The email in TestTokens.customer(...) */
    protected static final String CUSTOMER_EMAIL = "omesh@example.com";

    protected static final StubServices stub = StubServices.start();

    protected final UUID customerId = UUID.randomUUID();
    protected final UUID otherCustomerId = UUID.randomUUID();

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
        registry.add("services.cart-service.url", stub::baseUrl);
    }

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE wishlist_items, product_prices, outbox_events");
        stub.reset();
    }

    protected String customerAuth() {
        return "Bearer " + TestTokens.customer(customerId);
    }

    protected String otherCustomerAuth() {
        return "Bearer " + TestTokens.customer(otherCustomerId);
    }

    // ---------- API helpers ----------

    protected ResultActions add(String auth, UUID productId) throws Exception {
        return mockMvc.perform(post(ITEMS_URL).header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + productId + "\"}"));
    }

    /** The "data" of GET /api/v1/wishlist */
    protected JsonNode wishlist(String auth) throws Exception {
        MvcResult result = mockMvc.perform(get(WISHLIST_URL).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("data");
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected int rowCount(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    // ---------- Events ----------

    /** A product event as product-service sends it. */
    protected static ProductEvent productUpdated(UUID productId, String sellingPrice, long version) {
        return new ProductEvent(UUID.randomUUID(), EventTypes.PRODUCT_UPDATED, productId, "SKU", "Product SKU", "ACTIVE",
                sellingPrice == null ? null : new BigDecimal(sellingPrice),
                sellingPrice == null ? null : new BigDecimal(sellingPrice), version);
    }

    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
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
