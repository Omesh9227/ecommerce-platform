package com.ecommerce.inventoryservice.integration;

import com.ecommerce.inventoryservice.event.EventTypes;
import com.ecommerce.inventoryservice.event.ProductEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** product-events (from product-service) create and refresh inventory items. */
class ProductEventConsumptionIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private static String productEventJson(String type, UUID productId, String sku, String name, String status, long version) {
        // Same shape product-service publishes (extra fields such as price are ignored here)
        return """
                {"eventId":"%s","eventType":"%s","occurredAt":"2026-10-20T10:00:00Z","productId":"%s",
                 "sku":"%s","name":"%s","status":"%s","categoryId":"%s","sellingPrice":59999.00,"productVersion":%d}
                """.formatted(UUID.randomUUID(), type, productId, sku, name, status, UUID.randomUUID(), version);
    }

    private void apply(String type, UUID productId, String sku, String name, String status, long version) {
        productSnapshotService.apply(new ProductEvent(UUID.randomUUID(), type, Instant.now(), productId, sku, name,
                status, version));
    }

    @Test
    void productCreatedOnKafka_createsItemWithZeroStock() throws Exception {
        UUID productId = UUID.randomUUID();

        kafkaTemplate.send("product-events", productId.toString(),
                productEventJson(EventTypes.PRODUCT_CREATED, productId, "PIXEL-8", "Pixel 8", "ACTIVE", 0))
                .get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mockMvc.perform(get(INVENTORY_URL + "/" + productId).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.sku").value("PIXEL-8"))
                        .andExpect(jsonPath("$.data.productName").value("Pixel 8"))
                        .andExpect(jsonPath("$.data.productStatus").value("ACTIVE"))
                        .andExpect(jsonPath("$.data.quantityOnHand").value(0))
                        .andExpect(jsonPath("$.data.availableQuantity").value(0))
                        .andExpect(jsonPath("$.data.lowStockThreshold").value(5)));
    }

    @Test
    void productUpdate_refreshesSnapshot_butNeverTouchesStock() throws Exception {
        UUID productId = UUID.randomUUID();
        apply(EventTypes.PRODUCT_CREATED, productId, "PIXEL-8", "Pixel 8", "ACTIVE", 0);
        setStock(productId, 12);

        apply(EventTypes.PRODUCT_UPDATED, productId, "PIXEL-8-PRO", "Pixel 8 Pro", "INACTIVE", 1);

        var item = item(productId);
        assertThat(item.get("sku").asText()).isEqualTo("PIXEL-8-PRO");
        assertThat(item.get("productName").asText()).isEqualTo("Pixel 8 Pro");
        assertThat(item.get("productStatus").asText()).isEqualTo("INACTIVE");
        assertThat(item.get("quantityOnHand").asInt()).isEqualTo(12);
    }

    @Test
    void outdatedAndDuplicateEvents_areIgnored() throws Exception {
        UUID productId = UUID.randomUUID();
        // Arrives out of order: the update (v2) before the create (v0), and an older update (v1) last
        apply(EventTypes.PRODUCT_UPDATED, productId, "SKU-V2", "Name v2", "ACTIVE", 2);
        apply(EventTypes.PRODUCT_CREATED, productId, "SKU-V0", "Name v0", "ACTIVE", 0);
        apply(EventTypes.PRODUCT_UPDATED, productId, "SKU-V1", "Name v1", "INACTIVE", 1);
        apply(EventTypes.PRODUCT_UPDATED, productId, "SKU-V2", "Name v2", "ACTIVE", 2); // duplicate

        var item = item(productId);
        assertThat(item.get("sku").asText()).isEqualTo("SKU-V2");
        assertThat(item.get("productStatus").asText()).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM inventory_items WHERE product_id = ?",
                Long.class, productId)).isEqualTo(1L);
    }

    @Test
    void malformedEvent_goesToDeadLetterTopic_andDoesNotBlockLaterEvents() throws Exception {
        String badKey = "bad-" + UUID.randomUUID();
        kafkaTemplate.send("product-events", badKey, "this is not json").get(10, TimeUnit.SECONDS);

        UUID productId = UUID.randomUUID();
        kafkaTemplate.send("product-events", productId.toString(),
                productEventJson(EventTypes.PRODUCT_CREATED, productId, "AFTER-BAD", "After bad", "ACTIVE", 0))
                .get(10, TimeUnit.SECONDS);

        List<ConsumerRecord<String, String>> deadLetters = awaitRecords("product-events.DLT", badKey, 1);
        assertThat(deadLetters.get(0).value()).isEqualTo("this is not json");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mockMvc.perform(get(INVENTORY_URL + "/" + productId).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                        .andExpect(status().isOk()));
    }
}
