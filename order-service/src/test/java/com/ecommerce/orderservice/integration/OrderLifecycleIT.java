package com.ecommerce.orderservice.integration;

import com.ecommerce.orderservice.service.OrderExpiryJob;
import com.ecommerce.orderservice.support.StubServices.Service;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private OrderExpiryJob expiryJob;

    private ResultActions customerCancel(String auth, String orderNumber, String reason) throws Exception {
        var request = post(ORDERS_URL + "/" + orderNumber + "/cancel").header(HttpHeaders.AUTHORIZATION, auth);
        if (reason != null) {
            request.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}");
        }
        return mockMvc.perform(request);
    }

    private ResultActions admin(String orderNumber, String action) throws Exception {
        return mockMvc.perform(post(ADMIN_URL + "/" + orderNumber + "/" + action).header(HttpHeaders.AUTHORIZATION, adminAuth));
    }

    // ---------- Cancel ----------

    @Test
    void customerCancels_stockIsReleased_andCancellingAgainIsHarmless() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String orderNumber = placeOrder(customerAuth, phone, 3);
        assertThat(stub.stock(phone).reserved).isEqualTo(3);

        customerCancel(customerAuth, orderNumber, "Ordered by mistake")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.statusReason").value("Ordered by mistake"))
                .andExpect(jsonPath("$.data.cancelledAt").exists());
        customerCancel(customerAuth, orderNumber, null).andExpect(status().isOk());

        assertThat(stub.reservation(orderNumber).status).isEqualTo("RELEASED");
        assertThat(stub.stock(phone).reserved).isZero();

        String orderId = jdbcTemplate.queryForObject("SELECT id::text FROM orders WHERE order_number = ?", String.class, orderNumber);
        List<ConsumerRecord<String, String>> events = awaitEvents(orderId, 2);
        assertThat(events).extracting(r -> eventType(r.value()))
                .containsExactly("ORDER_PLACED", "ORDER_CANCELLED");
    }

    @Test
    void customers_canOnlySeeAndCancelTheirOwnOrders() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String orderNumber = placeOrder(customerAuth, phone, 1);

        mockMvc.perform(get(ORDERS_URL + "/" + orderNumber).header(HttpHeaders.AUTHORIZATION, otherCustomerAuth))
                .andExpect(status().isNotFound());
        customerCancel(otherCustomerAuth, orderNumber, null).andExpect(status().isNotFound());
        mockMvc.perform(get(ORDERS_URL).header(HttpHeaders.AUTHORIZATION, otherCustomerAuth))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get(ORDERS_URL + "/" + orderNumber).header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sku").value("PHONE"));
        mockMvc.perform(get(ORDERS_URL + "/" + orderNumber).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeline.length()").value(2)); // admins also see the internal CREATED step

        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void adminEndpoints_needAdmin() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String orderNumber = placeOrder(customerAuth, phone, 1);

        mockMvc.perform(get(ADMIN_URL).header(HttpHeaders.AUTHORIZATION, customerAuth)).andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN_URL + "/" + orderNumber + "/ship").header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ORDERS_URL)).andExpect(status().isUnauthorized());
    }

    // ---------- Full lifecycle ----------

    @Test
    void paid_shipped_delivered() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String orderNumber = placeOrder(customerAuth, phone, 2);

        pay(orderNumber);
        pay(orderNumber); // a repeated event changes nothing
        mockMvc.perform(get(ORDERS_URL + "/" + orderNumber).header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").exists())
                .andExpect(jsonPath("$.data.timeline[1].note").value("Paid by VISA ending 4242"));
        assertThat(stub.reservation(orderNumber).status).isEqualTo("CONFIRMED");
        assertThat(stub.stock(phone).onHand).isEqualTo(8);
        assertThat(stub.stock(phone).reserved).isZero();

        admin(orderNumber, "deliver").andExpect(status().isConflict()); // must ship first

        mockMvc.perform(post(ADMIN_URL + "/" + orderNumber + "/ship")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"Delhivery\",\"trackingNumber\":\"DLV123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.carrier").value("Delhivery"))
                .andExpect(jsonPath("$.data.trackingNumber").value("DLV123"));
        admin(orderNumber, "ship").andExpect(status().isOk()); // repeating is harmless
        customerCancel(customerAuth, orderNumber, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("on its way")));

        admin(orderNumber, "deliver")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.deliveredAt").exists());

        mockMvc.perform(get(ORDERS_URL + "/" + orderNumber).header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(jsonPath("$.data.timeline[*].toStatus").value(
                        org.hamcrest.Matchers.contains("PENDING_PAYMENT", "CONFIRMED", "SHIPPED", "DELIVERED")));

        String orderId = jdbcTemplate.queryForObject("SELECT id::text FROM orders WHERE order_number = ?", String.class, orderNumber);
        List<ConsumerRecord<String, String>> events = awaitEvents(orderId, 4);
        assertThat(events).extracting(r -> eventType(r.value()))
                .containsExactly("ORDER_PLACED", "ORDER_CONFIRMED", "ORDER_SHIPPED", "ORDER_DELIVERED");
        List<Long> versions = events.stream().map(r -> readVersion(r.value())).toList();
        assertThat(versions).isSorted().doesNotHaveDuplicates();
        JsonNode shipped = readEvent(events.get(2).value());
        assertThat(shipped.get("carrier").asText()).isEqualTo("Delhivery");
        assertThat(shipped.get("trackingNumber").asText()).isEqualTo("DLV123");
        assertThat(shipped.get("items").get(0).get("productName").asText()).isEqualTo("Product PHONE");
    }

    @Test
    void cancellingAPaidOrder_returnsTheStock_andTheRefundShowsInTheTimeline() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String orderNumber = placeOrder(customerAuth, phone, 2);
        pay(orderNumber);
        assertThat(stub.stock(phone).onHand).isEqualTo(8);

        customerCancel(customerAuth, orderNumber, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.statusReason").value("Cancelled by customer - a full refund has been requested"));
        assertThat(stub.reservation(orderNumber).status).isEqualTo("RETURNED");
        assertThat(stub.stock(phone).onHand).isEqualTo(10);

        // payment-service refunds and reports it (twice, to prove it is recorded once)
        paymentEventHandler.handle(paymentEvent("PAYMENT_REFUNDED", orderNumber));
        paymentEventHandler.handle(paymentEvent("PAYMENT_REFUNDED", orderNumber));
        mockMvc.perform(get(ORDERS_URL + "/" + orderNumber).header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.timeline.length()").value(4))
                .andExpect(jsonPath("$.data.timeline[3].note").value("Refund of 1800.00 INR completed"));

        List<ConsumerRecord<String, String>> events = awaitEvents(orderId(orderNumber).toString(), 3);
        assertThat(events).extracting(r -> eventType(r.value()))
                .containsExactly("ORDER_PLACED", "ORDER_CONFIRMED", "ORDER_CANCELLED");
    }

    @Test
    void paymentAfterTheOrderWasCancelled_isIgnored() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String orderNumber = placeOrder(customerAuth, phone, 1);
        customerCancel(customerAuth, orderNumber, null).andExpect(status().isOk());

        pay(orderNumber); // payment-service refunds it when it sees ORDER_CANCELLED

        assertThat(orderStatus(orderNumber)).isEqualTo("CANCELLED");
        assertThat(stub.reservation(orderNumber).status).isEqualTo("RELEASED");
    }

    @Test
    void inventoryDown_duringCancel_leavesTheOrderUnchanged() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String orderNumber = placeOrder(customerAuth, phone, 1);

        stub.setDown(Service.INVENTORY, true);
        customerCancel(customerAuth, orderNumber, null).andExpect(status().isServiceUnavailable());
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING_PAYMENT");

        stub.setDown(Service.INVENTORY, false);
        customerCancel(customerAuth, orderNumber, null).andExpect(status().isOk());
        assertThat(orderStatus(orderNumber)).isEqualTo("CANCELLED");
    }

    // ---------- Lists ----------

    @Test
    void myOrders_newestFirst_andAdminFilters() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String first = placeOrder(customerAuth, phone, 1);
        String second = placeOrder(customerAuth, phone, 1);
        placeOrder(otherCustomerAuth, phone, 1);
        customerCancel(customerAuth, first, null).andExpect(status().isOk());

        mockMvc.perform(get(ORDERS_URL).header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value(second));
        mockMvc.perform(get(ORDERS_URL).param("status", "CANCELLED").header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value(first));
        mockMvc.perform(get(ORDERS_URL).param("sort", "userEmail").header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(ADMIN_URL).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(jsonPath("$.data.totalElements").value(3));
        mockMvc.perform(get(ADMIN_URL).param("userId", otherCustomerId.toString()).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get(ADMIN_URL).param("status", "PENDING_PAYMENT").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    // ---------- Expiry ----------

    @Test
    void unpaidOrders_areCancelledAfterThePaymentWindow() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String unpaid = placeOrder(customerAuth, phone, 2);
        String paid = placeOrder(customerAuth, phone, 1);
        pay(paid);

        clock.advance(Duration.ofMinutes(29));
        assertThat(expiryJob.expireUnpaidOrders()).isZero();

        clock.advance(Duration.ofMinutes(2));
        assertThat(expiryJob.expireUnpaidOrders()).isEqualTo(1);

        assertThat(orderStatus(unpaid)).isEqualTo("CANCELLED");
        assertThat(orderStatus(paid)).isEqualTo("CONFIRMED");
        assertThat(stub.reservation(unpaid).status).isEqualTo("RELEASED");
        mockMvc.perform(get(ORDERS_URL + "/" + unpaid).header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(jsonPath("$.data.statusReason").value("Payment was not received in time"));
    }

    @Test
    void stuckCheckouts_areCleanedUp() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        stub.addToCart(customerAuth, phone, 1);
        stub.setDown(Service.INVENTORY, true);
        checkout(customerAuth, newKey(), checkoutJson()).andExpect(status().isServiceUnavailable());
        stub.setDown(Service.INVENTORY, false);

        assertThat(expiryJob.failStuckCheckouts()).isZero(); // too recent
        clock.advance(Duration.ofMinutes(3));
        assertThat(expiryJob.failStuckCheckouts()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders", String.class)).isEqualTo("CHECKOUT_FAILED");
    }

    private JsonNode readEvent(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String eventType(String json) {
        try {
            return objectMapper.readTree(json).get("eventType").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long readVersion(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.get("orderVersion").asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
