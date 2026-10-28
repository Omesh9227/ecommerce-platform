package com.ecommerce.orderservice.integration;

import com.ecommerce.orderservice.config.JwtProperties;
import com.ecommerce.orderservice.security.JwtValidator;
import com.ecommerce.orderservice.support.StubServices.Service;
import com.ecommerce.orderservice.support.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckoutIT extends AbstractIntegrationTest {

    @Test
    void checkout_placesOrder_reservesStock_clearsCart_andPublishesEvent() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        UUID phoneCase = stub.product("CASE", "200.00", "200.00", 10);
        stub.addToCart(customerAuth, phone, 2);
        stub.addToCart(customerAuth, phoneCase, 1);

        MvcResult result = checkout(customerAuth, newKey(), checkoutJson())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Order placed successfully"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andReturn();
        JsonNode order = readJson(result).get("data");

        String orderNumber = order.get("orderNumber").asText();
        assertThat(orderNumber).matches("ORD-[0-9A-F]{12}");
        assertThat(order.get("userId").asText()).isEqualTo(customerId.toString());
        assertThat(order.get("itemCount").asInt()).isEqualTo(3);
        assertThat(order.get("subtotal").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(order.get("shippingFee").decimalValue()).isEqualByComparingTo("0.00"); // over the free-shipping threshold
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(order.get("currency").asText()).isEqualTo("INR");
        assertThat(order.get("shippingAddress").get("postalCode").asText()).isEqualTo("560001");
        assertThat(order.get("shippingAddress").get("country").asText()).isEqualTo("IN");
        assertThat(Instant.parse(order.get("paymentDueAt").asText()))
                .isBetween(clock.instant().plus(Duration.ofMinutes(29)), clock.instant().plus(Duration.ofMinutes(31)));
        // The customer's timeline doesn't show the internal CREATED step
        assertThat(order.get("timeline")).hasSize(1);
        assertThat(order.get("timeline").get(0).get("toStatus").asText()).isEqualTo("PENDING_PAYMENT");

        // Stock is reserved under the order number, and the cart is empty
        assertThat(stub.reservation(orderNumber).status).isEqualTo("RESERVED");
        assertThat(stub.stock(phone).reserved).isEqualTo(2);
        assertThat(stub.cartSize(customerAuth)).isZero();
        assertThat(stub.cartClears()).isEqualTo(1);

        // inventory-service was called with order-service's own SERVICE token
        Claims serviceClaims = new JwtValidator(new JwtProperties(TestTokens.SECRET, TestTokens.ISSUER))
                .validate(stub.inventoryAuthHeaders().get(0).substring("Bearer ".length()));
        assertThat(serviceClaims.get("role", String.class)).isEqualTo("SERVICE");

        // ORDER_PLACED reaches Kafka, keyed by the order id
        List<ConsumerRecord<String, String>> events = awaitEvents(order.get("orderId").asText(), 1);
        JsonNode event = objectMapper.readTree(events.get(0).value());
        assertThat(event.get("eventType").asText()).isEqualTo("ORDER_PLACED");
        assertThat(event.get("orderNumber").asText()).isEqualTo(orderNumber);
        assertThat(event.get("items")).hasSize(2);
    }

    @Test
    void pricesComeFromProductService_andSmallOrdersPayShipping() throws Exception {
        UUID pen = stub.product("PEN", "60.00", "50.00", 10);
        stub.addToCart(customerAuth, pen, 2);

        checkout(customerAuth, newKey(), checkoutJson())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(50.00))
                .andExpect(jsonPath("$.data.items[0].listPrice").value(60.00))
                .andExpect(jsonPath("$.data.subtotal").value(100.00))
                .andExpect(jsonPath("$.data.shippingFee").value(40.00))
                .andExpect(jsonPath("$.data.totalAmount").value(140.00));
    }

    @Test
    void sameIdempotencyKey_returnsTheSameOrder_withoutReservingTwice() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        stub.addToCart(customerAuth, phone, 1);
        String key = newKey();

        String first = readJson(checkout(customerAuth, key, checkoutJson()).andExpect(status().isCreated()).andReturn())
                .at("/data/orderNumber").asText();
        stub.addToCart(customerAuth, phone, 5); // cart changed after the order: must not matter for a retry

        checkout(customerAuth, key, checkoutJson())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Order already placed with this Idempotency-Key"))
                .andExpect(jsonPath("$.data.orderNumber").value(first))
                .andExpect(jsonPath("$.data.itemCount").value(1));

        assertThat(stub.reserveCalls()).isEqualTo(1);
        assertThat(stub.cartClears()).isEqualTo(1);
        assertThat(stub.cartSize(customerAuth)).isEqualTo(1); // the new cart line was not cleared by the retry
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class)).isEqualTo(1L);
    }

    @Test
    void sameKeyFromAnotherUser_isAnotherOrder() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        stub.addToCart(customerAuth, phone, 1);
        stub.addToCart(otherCustomerAuth, phone, 1);
        String key = newKey();

        String mine = readJson(checkout(customerAuth, key, checkoutJson()).andExpect(status().isCreated()).andReturn())
                .at("/data/orderNumber").asText();
        String theirs = readJson(checkout(otherCustomerAuth, key, checkoutJson()).andExpect(status().isCreated()).andReturn())
                .at("/data/orderNumber").asText();

        assertThat(mine).isNotEqualTo(theirs);
    }

    @Test
    void idempotencyKey_isRequired() throws Exception {
        checkout(customerAuth, null, checkoutJson())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Idempotency-Key header is required")));
        checkout(customerAuth, "short", checkoutJson()).andExpect(status().isBadRequest());
        checkout(customerAuth, "has spaces in it", checkoutJson()).andExpect(status().isBadRequest());
    }

    @Test
    void invalidAddress_is400() throws Exception {
        checkout(customerAuth, newKey(), "{\"shippingAddress\":{\"fullName\":\"\",\"phone\":\"123\",\"line1\":\"x\","
                + "\"city\":\"Pune\",\"state\":\"MH\",\"postalCode\":\"012345\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors['shippingAddress.fullName']").exists())
                .andExpect(jsonPath("$.validationErrors['shippingAddress.phone']").exists())
                .andExpect(jsonPath("$.validationErrors['shippingAddress.postalCode']").exists());
        checkout(customerAuth, newKey(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.shippingAddress").exists());
    }

    @Test
    void cartProblems_areReported_andNoOrderIsCreated() throws Exception {
        // Empty cart
        checkout(customerAuth, newKey(), checkoutJson())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your cart is empty"));

        // An item that can't be bought
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 1);
        stub.addToCart(customerAuth, phone, 3, "INSUFFICIENT_STOCK", "Only 1 left in stock");
        checkout(customerAuth, newKey(), checkoutJson())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Product PHONE (Only 1 left in stock)")));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class)).isZero();
        assertThat(stub.reserveCalls()).isZero();
    }

    @Test
    void productNoLongerSold_orTotalChanged_isConflict() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        stub.addToCart(customerAuth, phone, 1);

        // The customer saw 900 + 0 shipping, but the price went up meanwhile
        stub.setSellingPrice(phone, "950.00");
        checkout(customerAuth, newKey(), "{\"shippingAddress\":" + addressJson() + ",\"expectedTotal\":900.00}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("The order total is now 950.00 INR")));

        stub.discontinue(phone);
        checkout(customerAuth, newKey(), checkoutJson())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("is no longer available")));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class)).isZero();
    }

    @Test
    void notEnoughStock_failsTheCheckout_andRetryGivesTheSameAnswer() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 1);
        stub.addToCart(customerAuth, phone, 2);
        String key = newKey();

        checkout(customerAuth, key, checkoutJson())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot reserve stock. PHONE: requested 2, available 1"));
        checkout(customerAuth, key, checkoutJson())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot reserve stock. PHONE: requested 2, available 1"));

        assertThat(stub.reserveCalls()).isEqualTo(1);
        assertThat(stub.cartSize(customerAuth)).isEqualTo(1); // cart kept so the customer can fix it

        // Customers don't see failed checkouts; admins do
        mockMvc.perform(get(ORDERS_URL).header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get(ADMIN_URL).param("status", "CHECKOUT_FAILED").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void downstreamDown_is503_andRetryingWithTheSameKeyFinishesTheOrder() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        stub.addToCart(customerAuth, phone, 1);
        String key = newKey();

        stub.setDown(Service.CART, true);
        checkout(customerAuth, key, checkoutJson())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(containsString("cart-service")));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class)).isZero();
        stub.setDown(Service.CART, false);

        stub.setDown(Service.INVENTORY, true);
        checkout(customerAuth, key, checkoutJson()).andExpect(status().isServiceUnavailable());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders", String.class)).isEqualTo("CREATED");

        stub.setDown(Service.INVENTORY, false);
        checkout(customerAuth, key, checkoutJson())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
        assertThat(stub.stock(phone).reserved).isEqualTo(1);
    }

    @Test
    void cartPricesNotRefreshed_is503() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        stub.addToCart(customerAuth, phone, 1);
        stub.setCartPricesUpToDate(customerAuth, false);

        checkout(customerAuth, newKey(), checkoutJson()).andExpect(status().isServiceUnavailable());
    }

    @Test
    void doubleClick_sameKeyInParallel_placesOneOrder() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        stub.addToCart(customerAuth, phone, 2);
        String key = newKey();
        int requests = 6;

        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                statuses.add(pool.submit(() -> {
                    start.await();
                    return checkout(customerAuth, key, checkoutJson()).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : statuses) {
                codes.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(codes).containsOnly(200, 201);
            assertThat(codes).filteredOn(c -> c == 201).hasSize(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class)).isEqualTo(1L);
        assertThat(stub.reservationCount()).isEqualTo(1);
        assertThat(stub.stock(phone).reserved).isEqualTo(2);
    }

    @Test
    void checkout_requiresACustomerOrAdminToken() throws Exception {
        checkout("Bearer not-a-token", newKey(), checkoutJson()).andExpect(status().isUnauthorized());
        // A service token is not a shopper
        checkout("Bearer " + TestTokens.service(UUID.randomUUID()), newKey(), checkoutJson())
                .andExpect(status().isForbidden());
    }
}
