package com.ecommerce.cartservice.integration;

import com.ecommerce.cartservice.dto.request.AddItemRequest;
import com.ecommerce.cartservice.exception.ApiException;
import com.ecommerce.cartservice.service.CartService;
import com.ecommerce.cartservice.support.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartControllerIT extends AbstractIntegrationTest {

    @Autowired
    private CartService cartService;

    private org.springframework.test.web.servlet.ResultActions updateQuantity(UUID productId, int quantity) throws Exception {
        return mockMvc.perform(put(ITEMS_URL + "/" + productId)
                .header(HttpHeaders.AUTHORIZATION, userAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":" + quantity + "}"));
    }

    private static JsonNode item(JsonNode cart, UUID productId) {
        for (JsonNode item : cart.get("items")) {
            if (item.get("productId").asText().equals(productId.toString())) {
                return item;
            }
        }
        throw new AssertionError("Product " + productId + " is not in the cart: " + cart);
    }

    // ---------- Security ----------

    @Test
    void cart_requiresToken() throws Exception {
        mockMvc.perform(get(CART_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get(CART_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.expiredAdmin(userId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminsHaveACartToo() throws Exception {
        mockMvc.perform(get(CART_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.admin(userId)))
                .andExpect(status().isOk());
    }

    // ---------- Add / view ----------

    @Test
    void newUser_hasAnEmptyCart() throws Exception {
        JsonNode cart = cart(userId);

        assertThat(cart.get("items")).isEmpty();
        assertThat(cart.get("subtotal").decimalValue()).isEqualByComparingTo("0");
        assertThat(cart.get("checkoutReady").asBoolean()).isFalse();
        assertThat(productService.lookups()).isZero();
    }

    @Test
    void addItems_thenViewWithLivePricesAndTotals() throws Exception {
        UUID phone = productService.put(UUID.randomUUID(), "PHONE", "Phone", "120.00", "100.00", 20);
        UUID phoneCase = productService.put("CASE", "15.50", 20);

        addItem(userId, phone, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Item added to cart"))
                .andExpect(jsonPath("$.data.totalQuantity").value(2));
        addItem(userId, phoneCase, 1).andExpect(status().isOk());
        addItem(userId, phone, 1).andExpect(status().isOk()); // adds to the existing line

        JsonNode cart = cart(userId);
        assertThat(cart.get("items")).hasSize(2);
        assertThat(cart.get("items").get(0).get("productId").asText()).isEqualTo(phoneCase.toString()); // newest first
        JsonNode phoneLine = item(cart, phone);
        assertThat(phoneLine.get("quantity").asInt()).isEqualTo(3);
        assertThat(phoneLine.get("unitPrice").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(phoneLine.get("listPrice").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(phoneLine.get("lineTotal").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(phoneLine.get("status").asText()).isEqualTo("AVAILABLE");
        assertThat(phoneLine.get("imageUrl").asText()).isEqualTo("https://cdn.example.com/phone.jpg");

        assertThat(cart.get("totalQuantity").asInt()).isEqualTo(4);
        assertThat(cart.get("subtotal").decimalValue()).isEqualByComparingTo("315.50");
        assertThat(cart.get("totalSavings").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(cart.get("currency").asText()).isEqualTo("INR");
        assertThat(cart.get("checkoutReady").asBoolean()).isTrue();
        assertThat(cart.get("pricesUpToDate").asBoolean()).isTrue();
    }

    @Test
    void addItem_rules() throws Exception {
        UUID scarce = productService.put("SCARCE", "100.00", 3);
        UUID plenty = productService.put("PLENTY", "100.00", 1000);
        UUID soldOut = productService.put("SOLD-OUT", "100.00", 0);

        addItem(userId, scarce, 2).andExpect(status().isOk());
        addItem(userId, scarce, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only 3 units of Product SCARCE are available (you already have 2 in your cart)"));
        addItem(userId, plenty, 11)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("at most 10 units")));
        addItem(userId, soldOut, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product SOLD-OUT is out of stock"));
        addItem(userId, UUID.randomUUID(), 1).andExpect(status().isNotFound());

        // Nothing above changed the cart
        assertThat(cart(userId).get("totalQuantity").asInt()).isEqualTo(2);
    }

    @Test
    void addItem_validation() throws Exception {
        addItem(userId, UUID.randomUUID(), 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.quantity").exists());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(ITEMS_URL)
                        .header(HttpHeaders.AUTHORIZATION, userAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.productId").exists());
    }

    // ---------- Update / remove / clear ----------

    @Test
    void updateQuantity_rules() throws Exception {
        UUID phone = productService.put("PHONE", "100.00", 4);
        addItem(userId, phone, 1).andExpect(status().isOk());

        updateQuantity(phone, 4).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalQuantity").value(4));
        updateQuantity(phone, 5).andExpect(status().isConflict());          // only 4 in stock
        updateQuantity(phone, 11).andExpect(status().isBadRequest());       // per-product limit
        updateQuantity(phone, 0).andExpect(status().isBadRequest());        // use DELETE instead
        updateQuantity(UUID.randomUUID(), 1).andExpect(status().isNotFound());

        // Decreasing works even when the product has sold out in the meantime
        productService.setStock(phone, 0);
        updateQuantity(phone, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].status").value("OUT_OF_STOCK"));
    }

    @Test
    void removeAndClear() throws Exception {
        UUID phone = productService.put("PHONE", "100.00", 5);
        UUID phoneCase = productService.put("CASE", "10.00", 5);
        addItem(userId, phone, 1);
        addItem(userId, phoneCase, 1);

        mockMvc.perform(delete(ITEMS_URL + "/" + phone).header(HttpHeaders.AUTHORIZATION, userAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mockMvc.perform(delete(ITEMS_URL + "/" + phone).header(HttpHeaders.AUTHORIZATION, userAuth()))
                .andExpect(status().isOk()); // removing again is fine

        mockMvc.perform(delete(CART_URL).header(HttpHeaders.AUTHORIZATION, userAuth()))
                .andExpect(status().isNoContent());
        assertThat(cart(userId).get("items")).isEmpty();
        assertThat(redis.hasKey("cart:" + userId)).isFalse();
    }

    // ---------- Live data ----------

    @Test
    void priceChange_stockDrop_andDiscontinuedProduct_areShown() throws Exception {
        UUID phone = productService.put("PHONE", "100.00", 10);
        UUID phoneCase = productService.put("CASE", "10.00", 10);
        UUID charger = productService.put("CHARGER", "20.00", 10);
        addItem(userId, phone, 1);
        addItem(userId, phoneCase, 3);
        addItem(userId, charger, 1);

        productService.setSellingPrice(phone, "90.00");
        productService.setStock(phoneCase, 2);
        productService.remove(charger);

        JsonNode cart = cart(userId);
        JsonNode phoneLine = item(cart, phone);
        assertThat(phoneLine.get("priceChanged").asBoolean()).isTrue();
        assertThat(phoneLine.get("priceWhenAdded").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(phoneLine.get("message").asText()).isEqualTo("Price dropped from 100.00 to 90.00");
        assertThat(item(cart, phoneCase).get("status").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(item(cart, phoneCase).get("message").asText()).isEqualTo("Only 2 left in stock");
        assertThat(item(cart, charger).get("status").asText()).isEqualTo("UNAVAILABLE");
        assertThat(item(cart, charger).get("name").asText()).isEqualTo("Product CHARGER");

        assertThat(cart.get("subtotal").decimalValue()).isEqualByComparingTo("90.00"); // only the phone can be bought
        assertThat(cart.get("checkoutReady").asBoolean()).isFalse();
    }

    @Test
    void productServiceDown_cartStillShows_butAddingFails() throws Exception {
        UUID phone = productService.put("PHONE", "100.00", 10);
        addItem(userId, phone, 2);

        productService.setDown(true);

        JsonNode cart = cart(userId);
        assertThat(cart.get("pricesUpToDate").asBoolean()).isFalse();
        assertThat(cart.get("checkoutReady").asBoolean()).isFalse();
        assertThat(item(cart, phone).get("status").asText()).isEqualTo("UNKNOWN");
        assertThat(item(cart, phone).get("lineTotal").decimalValue()).isEqualByComparingTo("200.00");

        addItem(userId, phone, 1)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(containsString("temporarily unavailable")));
    }

    // ---------- Isolation / expiry / concurrency ----------

    @Test
    void eachUserHasTheirOwnCart() throws Exception {
        UUID phone = productService.put("PHONE", "100.00", 10);
        addItem(userId, phone, 2);

        assertThat(cart(otherUserId).get("items")).isEmpty();
        mockMvc.perform(delete(CART_URL).header(HttpHeaders.AUTHORIZATION, auth(otherUserId)))
                .andExpect(status().isNoContent());
        assertThat(cart(userId).get("totalQuantity").asInt()).isEqualTo(2);
    }

    @Test
    void cartExpires_andTheClockRestartsOnUse() throws Exception {
        UUID phone = productService.put("PHONE", "100.00", 10);
        addItem(userId, phone, 1);
        String key = "cart:" + userId;

        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(30L * 24 * 3600 - 60, 30L * 24 * 3600);

        redis.expire(key, java.time.Duration.ofMinutes(5));
        cart(userId); // viewing restarts the clock
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isGreaterThan(29L * 24 * 3600);
    }

    @Test
    void concurrentAdds_neverExceedTheLimit() throws Exception {
        UUID phone = productService.put("PHONE", "100.00", 1000);
        int threads = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        cartService.addItem(userId, new AddItemRequest(phone, 1));
                        return true;
                    } catch (ApiException e) {
                        return false; // over the per-product limit
                    }
                }));
            }
            start.countDown();
            int added = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    added++;
                }
            }
            assertThat(added).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }
        assertThat(item(cart(userId), phone).get("quantity").asInt()).isEqualTo(10);
    }
}
