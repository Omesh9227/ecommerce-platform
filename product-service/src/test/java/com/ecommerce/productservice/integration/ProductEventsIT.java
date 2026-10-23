package com.ecommerce.productservice.integration;

import com.ecommerce.productservice.entity.OutboxEvent;
import com.ecommerce.productservice.entity.ProductStatus;
import com.ecommerce.productservice.event.EventTypes;
import com.ecommerce.productservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Product changes produce outbox events (same transaction), and the relay publishes them to Kafka. */
class ProductEventsIT extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void createUpdateDelete_writeEventsInOrder_andRelayPublishesThem() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        UUID id = createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", null, null));

        mockMvc.perform(put(PRODUCTS_URL + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productRequest("PIXEL-8", "Pixel 8 Pro", "Google", mobiles, "79999", null,
                                ProductStatus.INACTIVE))))
                .andExpect(status().isOk());

        mockMvc.perform(delete(PRODUCTS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isNoContent());

        List<OutboxEvent> events = outboxEventRepository.findByEventKeyOrderByCreatedAtAsc(id.toString());
        assertThat(events).extracting(OutboxEvent::getEventType)
                .containsExactly(EventTypes.PRODUCT_CREATED, EventTypes.PRODUCT_UPDATED, EventTypes.PRODUCT_DISCONTINUED);
        assertThat(events).allSatisfy(e -> assertThat(e.getTopic()).isEqualTo("product-events"));

        JsonNode created = objectMapper.readTree(events.get(0).getPayload());
        JsonNode updated = objectMapper.readTree(events.get(1).getPayload());
        JsonNode discontinued = objectMapper.readTree(events.get(2).getPayload());

        assertThat(created.get("productId").asText()).isEqualTo(id.toString());
        assertThat(created.get("sku").asText()).isEqualTo("PIXEL-8");
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(updated.get("name").asText()).isEqualTo("Pixel 8 Pro");
        assertThat(updated.get("status").asText()).isEqualTo("INACTIVE");
        assertThat(discontinued.get("status").asText()).isEqualTo("DISCONTINUED");

        // Prices travel with the events (wishlist-service watches them for price drops)
        assertThat(created.get("price").decimalValue()).isEqualByComparingTo("59999");
        assertThat(created.get("sellingPrice").decimalValue()).isEqualByComparingTo("59999");
        assertThat(updated.get("sellingPrice").decimalValue()).isEqualByComparingTo("79999");

        // Versions only go up, so consumers can drop out-of-date events
        long v1 = created.get("productVersion").asLong();
        long v2 = updated.get("productVersion").asLong();
        long v3 = discontinued.get("productVersion").asLong();
        assertThat(v1).isLessThan(v2);
        assertThat(v2).isLessThan(v3);

        // The relay sends everything to (embedded) Kafka and marks it published
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(outboxEventRepository.countByPublishedAtIsNull()).isZero());
    }

    @Test
    void rejectedRequest_writesNoEvent() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", null, null));

        // Duplicate SKU: the transaction rolls back, so no event either
        mockMvc.perform(post(PRODUCTS_URL)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productRequest("pixel-8", "Copy", "Google", mobiles, "100", null, null))))
                .andExpect(status().isConflict());

        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }
}
