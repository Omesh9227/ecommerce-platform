package com.ecommerce.wishlistservice.integration;

import com.ecommerce.wishlistservice.support.StubServices.Service;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Saving, listing (with live prices), removing, and the rules around them. */
class WishlistControllerIT extends AbstractIntegrationTest {

    @Test
    void save_thenList_withLivePriceAndStock() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 5);

        add(customerAuth(), phone)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Saved to your wishlist"))
                .andExpect(jsonPath("$.data.productId").value(phone.toString()))
                .andExpect(jsonPath("$.data.price").value(1800.00))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));

        JsonNode wishlist = wishlist(customerAuth());
        assertThat(wishlist.get("totalItems").asInt()).isEqualTo(1);
        assertThat(wishlist.get("maxItems").asInt()).isEqualTo(5);
        assertThat(wishlist.get("pricesUpToDate").asBoolean()).isTrue();
        JsonNode item = wishlist.get("items").get(0);
        assertThat(item.get("name").asText()).isEqualTo("Product PIXEL-8");
        assertThat(item.get("price").decimalValue()).isEqualByComparingTo("1800");
        assertThat(item.get("listPrice").decimalValue()).isEqualByComparingTo("2000");
        assertThat(item.get("priceWhenAdded").decimalValue()).isEqualByComparingTo("1800");
        assertThat(item.get("priceDropped").asBoolean()).isFalse();
        assertThat(item.get("availableQuantity").asInt()).isEqualTo(5);
        assertThat(rowCount("wishlist_items")).isEqualTo(1);
    }

    @Test
    void savingTwice_keepsOneEntry_andAnswers200() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 5);

        add(customerAuth(), phone).andExpect(status().isCreated());
        add(customerAuth(), phone)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Already in your wishlist"));

        assertThat(wishlist(customerAuth()).get("totalItems").asInt()).isEqualTo(1);
    }

    @Test
    void outOfStockProduct_canBeSaved() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 0);

        add(customerAuth(), phone)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.data.message").value("Out of stock"));
    }

    @Test
    void productNotForSale_returns404() throws Exception {
        add(customerAuth(), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("not available for sale")));
    }

    @Test
    void invalidRequest_returns400() throws Exception {
        mockMvc.perform(post(ITEMS_URL)
                        .header(HttpHeaders.AUTHORIZATION, customerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.productId").value("productId is required"));
    }

    @Test
    void priceChangesSinceSaving_areShown() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 5);
        UUID case_ = stub.product("CASE", "500", "400", 5);
        add(customerAuth(), phone).andExpect(status().isCreated());
        add(customerAuth(), case_).andExpect(status().isCreated());

        stub.setSellingPrice(phone, "1500");
        stub.setSellingPrice(case_, "450");

        JsonNode items = wishlist(customerAuth()).get("items");
        JsonNode cheaper = find(items, phone);
        assertThat(cheaper.get("price").decimalValue()).isEqualByComparingTo("1500");
        assertThat(cheaper.get("priceDifference").decimalValue()).isEqualByComparingTo("-300");
        assertThat(cheaper.get("priceDropped").asBoolean()).isTrue();
        assertThat(cheaper.get("message").asText()).isEqualTo("Price dropped from 1800.00 to 1500.00");

        JsonNode dearer = find(items, case_);
        assertThat(dearer.get("priceDropped").asBoolean()).isFalse();
        assertThat(dearer.get("message").asText()).isEqualTo("Price increased from 400.00 to 450.00");
    }

    @Test
    void productNoLongerSold_isShownAsUnavailable_fromTheSnapshot() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 5);
        add(customerAuth(), phone).andExpect(status().isCreated());

        stub.discontinue(phone);

        JsonNode item = wishlist(customerAuth()).get("items").get(0);
        assertThat(item.get("status").asText()).isEqualTo("UNAVAILABLE");
        assertThat(item.get("message").asText()).isEqualTo("No longer available");
        assertThat(item.get("name").asText()).isEqualTo("Product PIXEL-8");
        assertThat(item.get("price").decimalValue()).isEqualByComparingTo("1800");
    }

    @Test
    void productServiceDown_wishlistStillShown_butSavingFails() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 5);
        UUID other = stub.product("CASE", "500", "400", 5);
        add(customerAuth(), phone).andExpect(status().isCreated());

        stub.setDown(Service.PRODUCT, true);

        JsonNode wishlist = wishlist(customerAuth());
        assertThat(wishlist.get("pricesUpToDate").asBoolean()).isFalse();
        assertThat(wishlist.get("warnings")).hasSize(1);
        assertThat(wishlist.get("items").get(0).get("status").asText()).isEqualTo("UNKNOWN");
        assertThat(wishlist.get("items").get(0).get("price").decimalValue()).isEqualByComparingTo("1800");

        add(customerAuth(), other)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The product catalog is temporarily unavailable. Please try again shortly."));
    }

    @Test
    void wishlistFull_returns400() throws Exception {
        for (int i = 0; i < 5; i++) {
            add(customerAuth(), stub.product("P" + i, "100", "100", 1)).andExpect(status().isCreated());
        }

        add(customerAuth(), stub.product("ONE-MORE", "100", "100", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("at most 5 products")));

        // Saving one that is already there is still fine
        UUID first = UUID.fromString(wishlist(customerAuth()).get("items").get(4).get("productId").asText());
        add(customerAuth(), first).andExpect(status().isOk());
    }

    @Test
    void remove_isIdempotent() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 5);
        add(customerAuth(), phone).andExpect(status().isCreated());

        mockMvc.perform(delete(ITEMS_URL + "/" + phone).header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(ITEMS_URL + "/" + phone).header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(status().isNoContent());

        assertThat(wishlist(customerAuth()).get("totalItems").asInt()).isZero();
    }

    @Test
    void clear_emptiesOnlyMyWishlist() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 5);
        add(customerAuth(), phone).andExpect(status().isCreated());
        add(customerAuth(), stub.product("CASE", "500", "400", 5)).andExpect(status().isCreated());
        add(otherCustomerAuth(), phone).andExpect(status().isCreated());

        mockMvc.perform(delete(WISHLIST_URL).header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(status().isNoContent());

        assertThat(wishlist(customerAuth()).get("totalItems").asInt()).isZero();
        assertThat(wishlist(otherCustomerAuth()).get("totalItems").asInt()).isEqualTo(1);
    }

    @Test
    void contains_saysWhichProductsAreSaved() throws Exception {
        UUID saved = stub.product("PIXEL-8", "2000", "1800", 5);
        UUID notSaved = stub.product("CASE", "500", "400", 5);
        add(customerAuth(), saved).andExpect(status().isCreated());

        mockMvc.perform(get(WISHLIST_URL + "/contains").param("productIds", notSaved + "," + saved)
                        .header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productIds.length()").value(1))
                .andExpect(jsonPath("$.data.productIds[0]").value(saved.toString()));

        // Someone else's wishlist is separate
        mockMvc.perform(get(WISHLIST_URL + "/contains").param("productIds", saved.toString())
                        .header(HttpHeaders.AUTHORIZATION, otherCustomerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productIds.length()").value(0));
    }

    @Test
    void contains_withInvalidId_returns400() throws Exception {
        mockMvc.perform(get(WISHLIST_URL + "/contains").param("productIds", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eachCustomer_seesOnlyTheirOwnWishlist() throws Exception {
        add(customerAuth(), stub.product("PIXEL-8", "2000", "1800", 5)).andExpect(status().isCreated());

        assertThat(wishlist(otherCustomerAuth()).get("totalItems").asInt()).isZero();
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get(WISHLIST_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void simultaneousSaves_ofOneProduct_storeItOnce() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 5);

        List<Integer> statuses = runInParallel(8, () -> add(customerAuth(), phone).andReturn());

        assertThat(statuses).containsOnly(201, 200);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(rowCount("wishlist_items")).isEqualTo(1);
    }

    @Test
    void simultaneousSaves_ofManyProducts_neverGoPastTheLimit() throws Exception {
        List<UUID> products = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            products.add(stub.product("P" + i, "100", "100", 1));
        }
        List<Callable<MvcResult>> saves = new ArrayList<>();
        for (UUID product : products) {
            saves.add(() -> add(customerAuth(), product).andReturn());
        }

        List<Integer> statuses = runAll(saves);

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(5);
        assertThat(statuses).filteredOn(s -> s == 400).hasSize(5);
        assertThat(rowCount("wishlist_items")).isEqualTo(5);
    }

    // ---------- Helpers ----------

    private static JsonNode find(JsonNode items, UUID productId) {
        for (JsonNode item : items) {
            if (item.get("productId").asText().equals(productId.toString())) {
                return item;
            }
        }
        throw new AssertionError("Product " + productId + " not in the wishlist");
    }

    private List<Integer> runInParallel(int times, Callable<MvcResult> call) throws Exception {
        List<Callable<MvcResult>> calls = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            calls.add(call);
        }
        return runAll(calls);
    }

    private static List<Integer> runAll(List<Callable<MvcResult>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Integer> statuses = new ArrayList<>();
            for (Future<MvcResult> future : pool.invokeAll(calls)) {
                statuses.add(future.get().getResponse().getStatus());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
