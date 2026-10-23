package com.ecommerce.inventoryservice.integration;

import com.ecommerce.inventoryservice.entity.OutboxEvent;
import com.ecommerce.inventoryservice.event.EventTypes;
import com.ecommerce.inventoryservice.repository.OutboxEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every stock change writes INVENTORY_UPDATED (and INVENTORY_LOW when it first drops low) and they reach Kafka. */
class InventoryEventsIT extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private void adjust(UUID productId, int change) throws Exception {
        mockMvc.perform(post(INVENTORY_URL + "/" + productId + "/stock/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityChange\": " + change + "}"))
                .andExpect(status().isOk());
    }

    private List<OutboxEvent> eventsOfType(List<OutboxEvent> events, String type) {
        return events.stream().filter(e -> e.getEventType().equals(type)).toList();
    }

    @Test
    void stockChanges_writeEvents_lowStockAlertOncePerDrop_andRelayPublishesThem() throws Exception {
        UUID productId = createItem("PIXEL-8"); // threshold 5
        String key = productId.toString();

        setStock(productId, 10); // 10: fine
        adjust(productId, -5);   // 5: drops to threshold -> LOW
        adjust(productId, -1);   // 4: still low -> no second LOW
        adjust(productId, 20);   // 24: recovered
        adjust(productId, -20);  // 4: low again -> LOW

        List<OutboxEvent> events = outboxEventRepository.findByEventKeyOrderByCreatedAtAsc(key);
        assertThat(events).allSatisfy(e -> assertThat(e.getTopic()).isEqualTo("inventory-events"));
        assertThat(eventsOfType(events, EventTypes.INVENTORY_UPDATED)).hasSize(5);

        List<OutboxEvent> lowEvents = eventsOfType(events, EventTypes.INVENTORY_LOW);
        assertThat(lowEvents).hasSize(2);
        assertThat(lowEvents).extracting(e -> readLong(e.getPayload(), "availableQuantity"))
                .containsExactlyInAnyOrder(5L, 4L);

        // Versions grow with each change, so product-service can drop out-of-date events
        List<Long> versions = eventsOfType(events, EventTypes.INVENTORY_UPDATED).stream()
                .map(e -> readLong(e.getPayload(), "inventoryVersion")).sorted().toList();
        assertThat(versions).doesNotHaveDuplicates();

        // The relay publishes all of them to Kafka, keyed by productId (keeps per-product order)
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(outboxEventRepository.findByEventKeyOrderByCreatedAtAsc(key))
                        .allSatisfy(e -> assertThat(e.getPublishedAt()).isNotNull()));
        List<ConsumerRecord<String, String>> records = awaitRecords("inventory-events", key, 7);
        assertThat(records).hasSize(7);
    }

    @Test
    void rejectedChange_writesNoEvent() throws Exception {
        UUID productId = createItem("PIXEL-8");

        mockMvc.perform(post(INVENTORY_URL + "/" + productId + "/stock/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityChange\": -1}"))
                .andExpect(status().isBadRequest());

        assertThat(outboxEventRepository.findByEventKeyOrderByCreatedAtAsc(productId.toString())).isEmpty();
    }

    private long readLong(String payload, String field) {
        try {
            return objectMapper.readTree(payload).get(field).asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
