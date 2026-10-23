package com.ecommerce.inventoryservice.integration;

import com.ecommerce.inventoryservice.dto.request.ReservationRequest;
import com.ecommerce.inventoryservice.exception.ConflictException;
import com.ecommerce.inventoryservice.service.ReservationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    private static String reservationJson(String orderReference, Object... productIdAndQuantity) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < productIdAndQuantity.length; i += 2) {
            if (!items.isEmpty()) {
                items.append(',');
            }
            items.append("{\"productId\":\"").append(productIdAndQuantity[i])
                    .append("\",\"quantity\":").append(productIdAndQuantity[i + 1]).append('}');
        }
        return "{\"orderReference\":\"" + orderReference + "\",\"items\":[" + items + "]}";
    }

    private ResultActions reserve(String body) throws Exception {
        return mockMvc.perform(post(RESERVATIONS_URL)
                .header(HttpHeaders.AUTHORIZATION, adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions action(String orderReference, String action) throws Exception {
        return mockMvc.perform(post(RESERVATIONS_URL + "/" + orderReference + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, adminAuth()));
    }

    // ---------- Reserve ----------

    @Test
    void reserve_holdsStock_andRepeatingTheSameOrderIsSafe() throws Exception {
        UUID phone = createItem("PHONE");
        UUID phoneCase = createItem("CASE");
        setStock(phone, 10);
        setStock(phoneCase, 4);
        String body = reservationJson("ORD-1001", phone, 2, phoneCase, 4);

        reserve(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderReference").value("ORD-1001"))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        // Retry (e.g. after a timeout): same reservation, nothing reserved twice
        reserve(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reservation already exists"));

        JsonNode phoneStock = item(phone);
        assertThat(phoneStock.get("quantityOnHand").asInt()).isEqualTo(10);
        assertThat(phoneStock.get("quantityReserved").asInt()).isEqualTo(2);
        assertThat(phoneStock.get("availableQuantity").asInt()).isEqualTo(8);
        assertThat(item(phoneCase).get("availableQuantity").asInt()).isZero();

        mockMvc.perform(get(RESERVATIONS_URL + "/ORD-1001").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"));

        mockMvc.perform(get(INVENTORY_URL + "/" + phone + "/movements").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(jsonPath("$.data.content[0].movementType").value("RESERVE"))
                .andExpect(jsonPath("$.data.content[0].reference").value("ORD-1001"));
    }

    @Test
    void reserve_notEnoughStock_isAllOrNothing() throws Exception {
        UUID phone = createItem("PHONE");
        UUID phoneCase = createItem("CASE");
        setStock(phone, 10);
        setStock(phoneCase, 1);

        reserve(reservationJson("ORD-1002", phone, 2, phoneCase, 3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("CASE: requested 3, available 1")));

        assertThat(item(phone).get("quantityReserved").asInt()).isZero();
        mockMvc.perform(get(RESERVATIONS_URL + "/ORD-1002").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void reserve_productNotForSale_isConflict() throws Exception {
        UUID discontinued = createItem("OLD-PHONE", "DISCONTINUED");
        setStock(discontinued, 10);

        reserve(reservationJson("ORD-1003", discontinued, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("OLD-PHONE is not available for sale")));
    }

    @Test
    void reserve_unknownProduct_is400() throws Exception {
        reserve(reservationJson("ORD-1004", UUID.randomUUID(), 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("No inventory record")));
    }

    @Test
    void reserve_invalidRequests_are400() throws Exception {
        UUID phone = createItem("PHONE");

        reserve(reservationJson("bad ref!", phone, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.orderReference").exists());
        reserve("{\"orderReference\":\"ORD-1005\",\"items\":[]}")
                .andExpect(status().isBadRequest());
        reserve(reservationJson("ORD-1005", phone, 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservations_requireAdmin() throws Exception {
        mockMvc.perform(post(RESERVATIONS_URL)
                        .header(HttpHeaders.AUTHORIZATION, customerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson("ORD-1006", UUID.randomUUID(), 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void stockCannotBeSetBelowReserved() throws Exception {
        UUID phone = createItem("PHONE");
        setStock(phone, 10);
        reserve(reservationJson("ORD-1007", phone, 6)).andExpect(status().isCreated());

        mockMvc.perform(put(INVENTORY_URL + "/" + phone + "/stock")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\": 5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("6 unit(s) are reserved")));
    }

    @Test
    void serviceToken_canUseReservations_butNotStockApis() throws Exception {
        UUID phone = createItem("PHONE");
        setStock(phone, 10);
        String serviceAuth = "Bearer " + com.ecommerce.inventoryservice.support.TestTokens.service(UUID.randomUUID());

        mockMvc.perform(post(RESERVATIONS_URL)
                        .header(HttpHeaders.AUTHORIZATION, serviceAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson("ORD-SVC-1", phone, 2)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(RESERVATIONS_URL + "/ORD-SVC-1/confirm").header(HttpHeaders.AUTHORIZATION, serviceAuth))
                .andExpect(status().isOk());

        mockMvc.perform(get(INVENTORY_URL + "/" + phone).header(HttpHeaders.AUTHORIZATION, serviceAuth))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(INVENTORY_URL + "/" + phone + "/stock")
                        .header(HttpHeaders.AUTHORIZATION, serviceAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityOnHand\": 100}"))
                .andExpect(status().isForbidden());
    }

    // ---------- Release / confirm ----------

    @Test
    void release_returnsStock_isIdempotent_andCannotBeConfirmedAfter() throws Exception {
        UUID phone = createItem("PHONE");
        setStock(phone, 10);
        reserve(reservationJson("ORD-2001", phone, 4)).andExpect(status().isCreated());

        action("ORD-2001", "release").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RELEASED"));
        action("ORD-2001", "release").andExpect(status().isOk());
        action("ORD-2001", "confirm").andExpect(status().isConflict());

        JsonNode stock = item(phone);
        assertThat(stock.get("quantityOnHand").asInt()).isEqualTo(10);
        assertThat(stock.get("quantityReserved").asInt()).isZero();
    }

    @Test
    void confirm_removesSoldStock_isIdempotent_andCannotBeReleasedAfter() throws Exception {
        UUID phone = createItem("PHONE");
        setStock(phone, 10);
        reserve(reservationJson("ORD-2002", phone, 4)).andExpect(status().isCreated());

        action("ORD-2002", "confirm").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        action("ORD-2002", "confirm").andExpect(status().isOk());
        action("ORD-2002", "release").andExpect(status().isConflict());

        JsonNode stock = item(phone);
        assertThat(stock.get("quantityOnHand").asInt()).isEqualTo(6);
        assertThat(stock.get("quantityReserved").asInt()).isZero();

        mockMvc.perform(get(INVENTORY_URL + "/" + phone + "/movements").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(jsonPath("$.data.content[0].movementType").value("SALE"))
                .andExpect(jsonPath("$.data.content[0].quantityChange").value(-4));
    }

    @Test
    void unknownReservation_is404() throws Exception {
        action("ORD-404", "release").andExpect(status().isNotFound());
        mockMvc.perform(get(RESERVATIONS_URL + "/ORD-404").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isNotFound());
    }

    // ---------- Concurrency ----------

    @Test
    void concurrentOrders_neverOversell() throws Exception {
        UUID phone = createItem("HOT-PHONE");
        setStock(phone, 10);

        List<Boolean> outcomes = runConcurrently(25, i -> () -> {
            try {
                reservationService.reserve(new ReservationRequest("ORD-RUSH-" + i,
                        List.of(new ReservationRequest.Item(phone, 1))), adminId);
                return true;
            } catch (ConflictException e) {
                return false; // sold out
            }
        });

        assertThat(outcomes.stream().filter(ok -> ok).count()).isEqualTo(10);
        JsonNode stock = item(phone);
        assertThat(stock.get("quantityReserved").asInt()).isEqualTo(10);
        assertThat(stock.get("availableQuantity").asInt()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reservations WHERE order_reference LIKE 'ORD-RUSH-%'", Long.class)).isEqualTo(10L);
    }

    @Test
    void sameOrderSentConcurrently_isReservedOnce() throws Exception {
        UUID phone = createItem("PHONE");
        setStock(phone, 10);

        List<Boolean> created = runConcurrently(8, i -> () -> {
            try {
                return reservationService.reserve(new ReservationRequest("ORD-DUP",
                        List.of(new ReservationRequest.Item(phone, 2))), adminId).created();
            } catch (ConflictException e) {
                return false; // lost the race on the unique order reference
            }
        });

        assertThat(created.stream().filter(c -> c).count()).isEqualTo(1);
        assertThat(item(phone).get("quantityReserved").asInt()).isEqualTo(2);
    }

    /** Starts all tasks at the same moment and returns their results. */
    private static List<Boolean> runConcurrently(int count, java.util.function.IntFunction<Callable<Boolean>> task)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<Boolean> callable = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return callable.call();
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
