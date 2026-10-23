package com.ecommerce.productservice.integration;

import com.ecommerce.productservice.event.EventTypes;
import com.ecommerce.productservice.event.InventoryEvent;
import com.ecommerce.productservice.service.ProductAvailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** INVENTORY_UPDATED events from inventory-service update each product's availableQuantity / inStock. */
class InventoryAvailabilityIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ProductAvailabilityService availabilityService;

    private static String inventoryUpdatedJson(UUID productId, int available, long version) {
        // Same shape inventory-service publishes (extra fields are ignored by product-service)
        return """
                {"eventId":"%s","eventType":"INVENTORY_UPDATED","occurredAt":"2026-10-20T10:00:00Z",
                 "productId":"%s","sku":"ANY","productName":"Any","quantityOnHand":%d,"quantityReserved":0,
                 "availableQuantity":%d,"lowStockThreshold":5,"lowStock":false,"inventoryVersion":%d}
                """.formatted(UUID.randomUUID(), productId, available, available, version);
    }

    @Test
    void newProduct_isOutOfStockUntilInventoryReportsStock() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        UUID id = createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", null, null));

        mockMvc.perform(get(PRODUCTS_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableQuantity").value(0))
                .andExpect(jsonPath("$.data.inStock").value(false));

        kafkaTemplate.send("inventory-events", id.toString(), inventoryUpdatedJson(id, 7, 3))
                .get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mockMvc.perform(get(PRODUCTS_URL + "/" + id))
                        .andExpect(jsonPath("$.data.availableQuantity").value(7))
                        .andExpect(jsonPath("$.data.inStock").value(true)));
    }

    @Test
    void outdatedInventoryEvent_isIgnored() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        UUID id = createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", null, null));

        availabilityService.apply(new InventoryEvent(UUID.randomUUID(), EventTypes.INVENTORY_UPDATED, id, 5, 10));
        availabilityService.apply(new InventoryEvent(UUID.randomUUID(), EventTypes.INVENTORY_UPDATED, id, 1, 9));

        mockMvc.perform(get(PRODUCTS_URL + "/" + id))
                .andExpect(jsonPath("$.data.availableQuantity").value(5));
    }

    @Test
    void inventoryLowEvent_doesNotChangeAvailability() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        UUID id = createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", null, null));

        availabilityService.apply(new InventoryEvent(UUID.randomUUID(), EventTypes.INVENTORY_LOW, id, 2, 4));

        mockMvc.perform(get(PRODUCTS_URL + "/" + id))
                .andExpect(jsonPath("$.data.availableQuantity").value(0));
    }

    @Test
    void inStockFilter() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        UUID withStock = createProduct(productRequest("IN-STOCK", "Has stock", "A", mobiles, "100", null, null));
        createProduct(productRequest("NO-STOCK", "No stock", "A", mobiles, "100", null, null));
        availabilityService.apply(new InventoryEvent(UUID.randomUUID(), EventTypes.INVENTORY_UPDATED, withStock, 3, 1));

        mockMvc.perform(get(PRODUCTS_URL).param("inStock", "true"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("IN-STOCK"));

        mockMvc.perform(get(PRODUCTS_URL).param("inStock", "false"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("NO-STOCK"));

        mockMvc.perform(get(PRODUCTS_URL).param("sort", "availableQuantity,desc"))
                .andExpect(jsonPath("$.data.content[0].sku").value("IN-STOCK"));
    }
}
