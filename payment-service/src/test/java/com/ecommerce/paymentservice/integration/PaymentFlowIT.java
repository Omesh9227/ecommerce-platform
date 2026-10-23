package com.ecommerce.paymentservice.integration;

import com.ecommerce.paymentservice.event.EventTypes;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentFlowIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void orderPlacedOnKafka_createsAPendingPayment() throws Exception {
        UUID orderId = UUID.randomUUID();
        String json = """
                {"eventId":"%s","eventType":"ORDER_PLACED","occurredAt":"2026-10-23T10:00:00Z","orderId":"%s",
                 "orderNumber":"ORD-KAFKA00001","userId":"%s","userEmail":"c@example.com","status":"PENDING_PAYMENT",
                 "reason":null,"totalAmount":1840.00,"currency":"INR","items":[],"orderVersion":1}
                """.formatted(UUID.randomUUID(), orderId, customerId);
        kafkaTemplate.send("order-events", orderId.toString(), json).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mockMvc.perform(get(PAYMENTS_URL + "/ORD-KAFKA00001").header(HttpHeaders.AUTHORIZATION, customerAuth()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("PENDING"))
                        .andExpect(jsonPath("$.data.amount").value(1840.00)));
    }

    @Test
    void cardPayment_succeeds_isPublished_andNeverStoresTheCardNumber() throws Exception {
        UUID orderId = orderPlaced("ORD-000000000001", "1800.00");
        String key = newKey();

        pay(customerAuth(), "ORD-000000000001", key, cardJson("4242 4242 4242 4242"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment successful"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.method").value("CARD"))
                .andExpect(jsonPath("$.data.cardBrand").value("VISA"))
                .andExpect(jsonPath("$.data.cardLast4").value("4242"))
                .andExpect(jsonPath("$.data.amount").value(1800.00))
                .andExpect(jsonPath("$.data.attempts.length()").value(1));

        // Same key again: same answer, nothing charged twice
        pay(customerAuth(), "ORD-000000000001", key, cardJson("4242 4242 4242 4242"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempts.length()").value(1));
        // A new attempt for a paid order
        pay(customerAuth(), "ORD-000000000001", newKey(), cardJson("4242 4242 4242 4242"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already paid")));

        Long rowsWithCardNumber = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_attempts a JOIN payments p ON p.id = a.payment_id "
                        + "WHERE CONCAT(a.card_last4, a.card_brand, a.provider_reference, p.card_last4) LIKE '%424242424242%'",
                Long.class);
        assertThat(rowsWithCardNumber).isZero();

        List<ConsumerRecord<String, String>> events = awaitRecords("payment-events", orderId.toString(), 1);
        JsonNode event = objectMapper.readTree(events.get(0).value());
        assertThat(event.get("eventType").asText()).isEqualTo(EventTypes.PAYMENT_SUCCEEDED);
        assertThat(event.get("cardLast4").asText()).isEqualTo("4242");
        assertThat(events.get(0).value()).doesNotContain("4242424242424242");
    }

    @Test
    void declinedCard_is402_thenAnotherMethodWorks() throws Exception {
        UUID orderId = orderPlaced("ORD-000000000002", "500.00");
        String declinedKey = newKey();

        pay(customerAuth(), "ORD-000000000002", declinedKey, cardJson("4000 0000 0000 0002"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.message").value("Your card was declined."));
        // Retrying the declined request gives the same answer (not a second charge attempt)
        pay(customerAuth(), "ORD-000000000002", declinedKey, cardJson("4000 0000 0000 0002"))
                .andExpect(status().isPaymentRequired());
        assertThat(paymentStatus("ORD-000000000002")).isEqualTo("PENDING");

        pay(customerAuth(), "ORD-000000000002", newKey(), upiJson("omesh.kumar@okhdfc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("UPI"))
                .andExpect(jsonPath("$.data.upiIdMasked").value("om*********@okhdfc"))
                .andExpect(jsonPath("$.data.attempts.length()").value(2))
                .andExpect(jsonPath("$.data.attempts[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.attempts[0].failureCode").value("card_declined"));

        List<ConsumerRecord<String, String>> events = awaitRecords("payment-events", orderId.toString(), 2);
        assertThat(events).extracting(r -> eventType(r.value()))
                .containsExactly(EventTypes.PAYMENT_FAILED, EventTypes.PAYMENT_SUCCEEDED);
    }

    @Test
    void invalidRequests_are400_andProviderDownIs503() throws Exception {
        orderPlaced("ORD-000000000003", "500.00");

        pay(customerAuth(), "ORD-000000000003", null, cardJson("4242424242424242"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Idempotency-Key")));
        pay(customerAuth(), "ORD-000000000003", newKey(), cardJson("4242424242424241"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The card number is not valid"));
        pay(customerAuth(), "ORD-000000000003", newKey(),
                "{\"method\":\"CARD\",\"card\":{\"number\":\"4242424242424242\",\"expiryMonth\":1,\"expiryYear\":2020,\"cvv\":\"123\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The card has expired"));
        pay(customerAuth(), "ORD-000000000003", newKey(), "{\"method\":\"CARD\",\"card\":{\"number\":\"4242\",\"cvv\":\"1\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors['card.number']").exists())
                .andExpect(jsonPath("$.validationErrors['card.cvv']").exists());
        pay(customerAuth(), "ORD-000000000003", newKey(), upiJson("not-an-upi-id"))
                .andExpect(status().isBadRequest());

        pay(customerAuth(), "ORD-000000000003", newKey(), cardJson("4000000000000119"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(get(PAYMENTS_URL + "/ORD-000000000003").header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.attempts.length()").value(0));
    }

    @Test
    void customers_canOnlySeeAndPayTheirOwnPayments() throws Exception {
        orderPlaced("ORD-000000000004", "500.00");

        mockMvc.perform(get(PAYMENTS_URL + "/ORD-000000000004").header(HttpHeaders.AUTHORIZATION, otherCustomerAuth()))
                .andExpect(status().isNotFound());
        pay(otherCustomerAuth(), "ORD-000000000004", newKey(), cardJson("4242424242424242"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PAYMENTS_URL + "/ORD-000000000004").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk());

        // An order payment-service hasn't heard of yet
        pay(customerAuth(), "ORD-UNKNOWN00000", newKey(), cardJson("4242424242424242"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("try again in a few seconds")));

        mockMvc.perform(get(ADMIN_URL).header(HttpHeaders.AUTHORIZATION, customerAuth())).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_URL).param("status", "PENDING").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get(PAYMENTS_URL + "/ORD-000000000004")).andExpect(status().isUnauthorized());
    }

    @Test
    void cancelledOrder_beforePayment_cantBePaid() throws Exception {
        UUID orderId = orderPlaced("ORD-000000000005", "500.00");
        orderCancelled(orderId, "ORD-000000000005");

        assertThat(paymentStatus("ORD-000000000005")).isEqualTo("CANCELLED");
        pay(customerAuth(), "ORD-000000000005", newKey(), cardJson("4242424242424242"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("cancelled")));
    }

    @Test
    void cancelledOrder_afterPayment_isRefunded() throws Exception {
        UUID orderId = orderPlaced("ORD-000000000006", "1800.00");
        pay(customerAuth(), "ORD-000000000006", newKey(), cardJson("4242424242424242")).andExpect(status().isOk());

        orderCancelled(orderId, "ORD-000000000006");
        orderCancelled(orderId, "ORD-000000000006"); // delivered twice: refunded once

        mockMvc.perform(get(PAYMENTS_URL + "/ORD-000000000006").header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refundReason").value("Order cancelled: Changed my mind"))
                .andExpect(jsonPath("$.data.refundedAt").exists());

        List<ConsumerRecord<String, String>> events = awaitRecords("payment-events", orderId.toString(), 2);
        assertThat(events).extracting(r -> eventType(r.value()))
                .containsExactly(EventTypes.PAYMENT_SUCCEEDED, EventTypes.PAYMENT_REFUNDED);
    }

    @Test
    void twoPaymentsAtOnce_onlyOneCharges() throws Exception {
        orderPlaced("ORD-000000000007", "500.00");
        int requests = 5;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return pay(customerAuth(), "ORD-000000000007", newKey(), cardJson("4242424242424242"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : results) {
                codes.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(codes).filteredOn(c -> c == 200).hasSize(1);
            assertThat(codes).filteredOn(c -> c == 409).hasSize(requests - 1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_attempts WHERE status = 'SUCCEEDED'", Long.class)).isEqualTo(1L);
    }
}
