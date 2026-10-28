package com.ecommerce.inventoryservice.integration;

import com.ecommerce.inventoryservice.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryControllerIT extends AbstractIntegrationTest {

    private org.springframework.test.web.servlet.ResultActions adjust(UUID productId, String body) throws Exception {
        return mockMvc.perform(post(INVENTORY_URL + "/" + productId + "/stock/adjustments")
                .header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // ---------- Security ----------

    @Test
    void inventory_requiresAdmin() throws Exception {
        UUID productId = createItem("PIXEL-8");

        mockMvc.perform(get(INVENTORY_URL + "/" + productId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get(INVENTORY_URL + "/" + productId).header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(INVENTORY_URL + "/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.expiredAdmin(adminId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(INVENTORY_URL + "/" + productId).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk());
    }

    // ---------- Read ----------

    @Test
    void getItem_unknownProduct_is404() throws Exception {
        mockMvc.perform(get(INVENTORY_URL + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getItem_invalidUuid_is400() throws Exception {
        mockMvc.perform(get(INVENTORY_URL + "/not-a-uuid").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_filtersBySearchAndLowStock_andSortsBySku() throws Exception {
        UUID pixel = createItem("PIXEL-8");
        UUID iphone = createItem("IPHONE-15");
        UUID galaxy = createItem("GALAXY-S24");
        setStock(pixel, 50);
        setStock(iphone, 3);   // low (threshold 5)
        setStock(galaxy, 100);

        mockMvc.perform(get(INVENTORY_URL).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].sku").value("GALAXY-S24"))
                .andExpect(jsonPath("$.data.content[2].sku").value("PIXEL-8"));

        mockMvc.perform(get(INVENTORY_URL).param("lowStock", "true").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("IPHONE-15"))
                .andExpect(jsonPath("$.data.content[0].lowStock").value(true));

        mockMvc.perform(get(INVENTORY_URL).param("search", "pixel").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productId").value(pixel.toString()));

        mockMvc.perform(get(INVENTORY_URL).param("sort", "quantityOnHand,desc").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(jsonPath("$.data.content[0].sku").value("GALAXY-S24"));
    }

    @Test
    void list_unknownSortField_is400() throws Exception {
        mockMvc.perform(get(INVENTORY_URL).param("sort", "version").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isBadRequest());
    }

    // ---------- Set / adjust ----------

    @Test
    void setStock_thenAdjust_recordsMovementsNewestFirst() throws Exception {
        UUID productId = createItem("PIXEL-8");

        mockMvc.perform(put(INVENTORY_URL + "/" + productId + "/stock")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\": 20, \"reason\": \"Opening stock\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.quantityOnHand").value(20))
                .andExpect(jsonPath("$.data.availableQuantity").value(20))
                .andExpect(jsonPath("$.data.lowStock").value(false));

        adjust(productId, "{\"quantityChange\": 30, \"reason\": \"Delivery\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantityOnHand").value(50));

        adjust(productId, "{\"quantityChange\": -4, \"reason\": \"Damaged\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantityOnHand").value(46));

        mockMvc.perform(get(INVENTORY_URL + "/" + productId + "/movements").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].movementType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.data.content[0].quantityChange").value(-4))
                .andExpect(jsonPath("$.data.content[0].quantityOnHandAfter").value(46))
                .andExpect(jsonPath("$.data.content[0].reason").value("Damaged"))
                .andExpect(jsonPath("$.data.content[0].actorId").value(adminId.toString()))
                .andExpect(jsonPath("$.data.content[1].movementType").value("RESTOCK"))
                .andExpect(jsonPath("$.data.content[2].quantityChange").value(20));
    }

    @Test
    void adjust_invalidRequests_are400() throws Exception {
        UUID productId = createItem("PIXEL-8");
        setStock(productId, 5);

        adjust(productId, "{\"quantityChange\": 0}").andExpect(status().isBadRequest());
        adjust(productId, "{\"quantityChange\": -6}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("only 5 available")));
        adjust(productId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.quantityChange").exists());

        mockMvc.perform(put(INVENTORY_URL + "/" + productId + "/stock")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\": -1}"))
                .andExpect(status().isBadRequest());

        // Nothing changed
        mockMvc.perform(get(INVENTORY_URL + "/" + productId).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(jsonPath("$.data.quantityOnHand").value(5));
    }

    @Test
    void stockChange_unknownProduct_is404() throws Exception {
        adjust(UUID.randomUUID(), "{\"quantityChange\": 5}").andExpect(status().isNotFound());
    }

    @Test
    void threshold_controlsLowStockFlag() throws Exception {
        UUID productId = createItem("PIXEL-8");
        setStock(productId, 8);

        mockMvc.perform(put(INVENTORY_URL + "/" + productId + "/threshold")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lowStockThreshold\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowStockThreshold").value(10))
                .andExpect(jsonPath("$.data.lowStock").value(true));

        mockMvc.perform(put(INVENTORY_URL + "/" + productId + "/threshold")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lowStockThreshold\": -1}"))
                .andExpect(status().isBadRequest());
    }
}
