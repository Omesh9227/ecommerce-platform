package com.ecommerce.productservice.integration;

import com.ecommerce.productservice.dto.request.CategoryRequest;
import com.ecommerce.productservice.entity.OutboxEvent;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Category changes go out on category-events; the whole catalog can be sent again for a new consumer. */
class CatalogEventsIT extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private List<JsonNode> payloads(String key) throws Exception {
        List<JsonNode> nodes = new java.util.ArrayList<>();
        for (OutboxEvent e : outboxEventRepository.findByEventKeyOrderByCreatedAtAsc(key)) {
            nodes.add(objectMapper.readTree(e.getPayload()));
        }
        return nodes;
    }

    @Test
    void createRenameMoveDelete_publishCategoryEvents() throws Exception {
        UUID electronics = createCategory("Electronics", null);
        UUID phones = createCategory("Phones", electronics);

        mockMvc.perform(put(CATEGORIES_URL + "/" + phones).header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON).content(json(new CategoryRequest("Mobiles", null, null))))
                .andExpect(status().isOk());
        mockMvc.perform(delete(CATEGORIES_URL + "/" + phones).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isNoContent());

        List<OutboxEvent> events = outboxEventRepository.findByEventKeyOrderByCreatedAtAsc(phones.toString());
        assertThat(events).extracting(OutboxEvent::getEventType)
                .containsExactly(EventTypes.CATEGORY_CREATED, EventTypes.CATEGORY_UPDATED, EventTypes.CATEGORY_DELETED);
        assertThat(events).allSatisfy(e -> assertThat(e.getTopic()).isEqualTo("category-events"));
        List<JsonNode> p = payloads(phones.toString());
        assertThat(p.get(0).get("name").asText()).isEqualTo("Phones");
        assertThat(p.get(0).get("parentId").asText()).isEqualTo(electronics.toString());
        assertThat(p.get(1).get("name").asText()).isEqualTo("Mobiles");
        assertThat(p.get(1).get("parentId").isNull()).isTrue(); // moved to the top level
        assertThat(p.get(0).get("categoryVersion").asLong()).isLessThan(p.get(1).get("categoryVersion").asLong());
        assertThat(p.get(1).get("categoryVersion").asLong()).isLessThan(p.get(2).get("categoryVersion").asLong());

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(outboxEventRepository.countByPublishedAtIsNull()).isZero());
    }

    @Test
    void republish_sendsEveryCategoryAndProductAgain_withCurrentVersions() throws Exception {
        UUID electronics = createCategory("Electronics", null);
        UUID phones = createCategory("Phones", electronics);
        UUID pixel = createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", phones, "59999", "10", null));
        UUID cable = createProduct(productRequest("CABLE-1", "USB-C Cable", "Anker", electronics, "499", null, null));
        mockMvc.perform(delete(PRODUCTS_URL + "/" + cable).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(PRODUCTS_URL + "/events/republish").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories").value(2))
                .andExpect(jsonPath("$.data.products").value(2));

        List<JsonNode> pixelEvents = payloads(pixel.toString());
        JsonNode again = pixelEvents.get(pixelEvents.size() - 1);
        assertThat(again.get("eventType").asText()).isEqualTo("PRODUCT_UPDATED");
        assertThat(again.get("productVersion").asLong()).isEqualTo(pixelEvents.get(0).get("productVersion").asLong());
        assertThat(again.get("discountPercent").decimalValue()).isEqualByComparingTo("10");
        List<JsonNode> cableEvents = payloads(cable.toString());
        assertThat(cableEvents.get(cableEvents.size() - 1).get("status").asText()).isEqualTo("DISCONTINUED");
        List<JsonNode> phonesEvents = payloads(phones.toString());
        assertThat(phonesEvents).extracting(n -> n.get("eventType").asText()).containsExactly("CATEGORY_CREATED", "CATEGORY_UPDATED");

        mockMvc.perform(post(PRODUCTS_URL + "/events/republish").header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PRODUCTS_URL + "/events/republish")).andExpect(status().isUnauthorized());
    }
}
