package com.ecommerce.inventoryservice.integration;

import com.ecommerce.inventoryservice.event.EventTypes;
import com.ecommerce.inventoryservice.event.ProductEvent;
import com.ecommerce.inventoryservice.service.ProductSnapshotService;
import com.ecommerce.inventoryservice.support.TestTokens;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context + real PostgreSQL (ecommerce_inventory_test_db) + the real security filter chain
 * + an in-memory Kafka broker (no local Kafka needed).
 * Tokens are signed with the test key, exactly as user-service would sign them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"product-events", "inventory-events"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public abstract class AbstractIntegrationTest {

    protected static final String INVENTORY_URL = "/api/v1/inventory";
    protected static final String RESERVATIONS_URL = "/api/v1/inventory/reservations";

    protected final UUID adminId = UUID.randomUUID();
    protected final UUID customerId = UUID.randomUUID();

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected ProductSnapshotService productSnapshotService;
    /** Address of the embedded broker (set by @EmbeddedKafka). */
    @Value("${spring.kafka.bootstrap-servers}")
    protected String bootstrapServers;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE reservation_items, reservations, stock_movements, inventory_items, outbox_events");
    }

    protected String adminAuth() {
        return "Bearer " + TestTokens.admin(adminId);
    }

    protected String customerAuth() {
        return "Bearer " + TestTokens.customer(customerId);
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Creates an ACTIVE inventory item with 0 stock, the same way a PRODUCT_CREATED event would. */
    protected UUID createItem(String sku) {
        return createItem(sku, "ACTIVE");
    }

    protected UUID createItem(String sku, String productStatus) {
        UUID productId = UUID.randomUUID();
        productSnapshotService.apply(new ProductEvent(UUID.randomUUID(), EventTypes.PRODUCT_CREATED, Instant.now(),
                productId, sku, "Product " + sku, productStatus, 0));
        return productId;
    }

    /** Sets on-hand stock through the API as admin. */
    protected void setStock(UUID productId, int quantity) throws Exception {
        mockMvc.perform(put(INVENTORY_URL + "/" + productId + "/stock")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\": " + quantity + "}"))
                .andExpect(status().isOk());
    }

    /** Current stock of a product as the API returns it (the "data" object). */
    protected JsonNode item(UUID productId) throws Exception {
        MvcResult result = mockMvc.perform(get(INVENTORY_URL + "/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).get("data");
    }

    /**
     * Reads a topic from the beginning (fresh consumer group) until {@code expected} records with this key arrived.
     * Other tests write to the same topics, so records are filtered by key.
     */
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
