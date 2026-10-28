package com.ecommerce.shippingservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Warehouse and carrier updates, and the shipment-events they produce. */
class ShipmentJourneyIT extends AbstractIntegrationTest {

    @Test
    void fromWarehouseToDoorstepWithOneMissedDelivery() throws Exception {
        clock.set(Instant.parse("2026-10-24T06:00:00Z"));
        UUID orderId = UUID.randomUUID();
        UUID shipmentId = shipmentFor(orderId, customerId);

        pack(shipmentId).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PACKED"));
        clock.set(Instant.parse("2026-10-26T06:00:00Z")); // shipped two days later: the estimate moves with it
        JsonNode shipped = readJson(ship(shipmentId, "Delhivery", null).andExpect(status().isOk()).andReturn()).get("data");
        String trackingNumber = shipped.get("trackingNumber").asText();
        assertThat(trackingNumber).matches("EC\\d{12}");
        assertThat(shipped.get("carrier").asText()).isEqualTo("Delhivery");
        assertThat(shipped.get("estimatedDeliveryDate").asText()).isEqualTo("2026-10-27");

        scan(shipmentId, "IN_TRANSIT", "Bengaluru hub", null).andExpect(status().isOk());
        scan(shipmentId, "IN_TRANSIT", "bengaluru hub", null).andExpect(status().isOk()); // same scan again: no new line
        scan(shipmentId, "IN_TRANSIT", "Hosur hub", null).andExpect(status().isOk());
        scan(shipmentId, "OUT_FOR_DELIVERY", "Koramangala", null).andExpect(status().isOk());
        scan(shipmentId, "DELIVERY_FAILED", "Koramangala", "Customer not available").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusReason").value("Customer not available"))
                .andExpect(jsonPath("$.data.failedAttempts").value(1));
        scan(shipmentId, "OUT_FOR_DELIVERY", "Koramangala", null).andExpect(status().isOk());
        JsonNode delivered = readJson(scan(shipmentId, "DELIVERED", "Koramangala", "Received by security")
                .andExpect(status().isOk()).andReturn()).get("data");

        assertThat(delivered.get("status").asText()).isEqualTo("DELIVERED");
        assertThat(delivered.has("statusReason")).isFalse();
        assertThat(delivered.get("deliveredAt").asText()).startsWith("2026-10-26");
        List<String> lines = new ArrayList<>();
        delivered.get("tracking").forEach(t -> lines.add(t.get("status").asText()
                + (t.has("location") ? " @ " + t.get("location").asText() : "")));
        assertThat(lines).containsExactly("PENDING", "PACKED", "SHIPPED @ Bengaluru warehouse",
                "IN_TRANSIT @ Bengaluru hub", "IN_TRANSIT @ Hosur hub", "OUT_FOR_DELIVERY @ Koramangala",
                "DELIVERY_FAILED @ Koramangala", "OUT_FOR_DELIVERY @ Koramangala", "DELIVERED @ Koramangala");
        assertThat(delivered.at("/tracking/8/description").asText()).isEqualTo("Delivered - Received by security");

        // order-service and notification-service hear about the steps that matter to the customer
        List<ConsumerRecord<String, String>> records = awaitRecords("shipment-events", orderId.toString(), 5);
        List<String> types = records.stream().map(r -> readTree(r.value()).get("eventType").asText()).toList();
        assertThat(types).containsExactly("SHIPMENT_SHIPPED", "SHIPMENT_OUT_FOR_DELIVERY", "SHIPMENT_DELIVERY_FAILED",
                "SHIPMENT_OUT_FOR_DELIVERY", "SHIPMENT_DELIVERED");
        long previous = -1;
        for (ConsumerRecord<String, String> record : records) {
            long version = readTree(record.value()).get("shipmentVersion").asLong();
            assertThat(version).isGreaterThan(previous);
            previous = version;
        }
        JsonNode shippedEvent = readTree(records.get(0).value());
        assertThat(shippedEvent.get("trackingNumber").asText()).isEqualTo(trackingNumber);
        assertThat(shippedEvent.get("carrier").asText()).isEqualTo("Delhivery");
        assertThat(shippedEvent.get("estimatedDeliveryDate").asText()).isEqualTo("2026-10-27");
        assertThat(shippedEvent.get("orderNumber").asText()).isEqualTo(orderNumber(orderId));
        assertThat(shippedEvent.get("userEmail").asText()).isEqualTo("omesh@example.com");
        JsonNode failedEvent = readTree(records.get(2).value());
        assertThat(failedEvent.get("note").asText()).isEqualTo("Customer not available");
        assertThat(failedEvent.get("failedAttempts").asInt()).isEqualTo(1);
    }

    @Test
    void repeatingAnUpdateChangesNothing() throws Exception {
        UUID shipmentId = shipmentFor(UUID.randomUUID(), customerId);
        pack(shipmentId).andExpect(status().isOk());
        pack(shipmentId).andExpect(status().isOk());
        String tracking = readJson(ship(shipmentId, "Blue Dart", "BD-77001122").andExpect(status().isOk()).andReturn())
                .at("/data/trackingNumber").asText();
        assertThat(tracking).isEqualTo("BD-77001122");
        ship(shipmentId, "blue dart", "bd-77001122").andExpect(status().isOk());
        ship(shipmentId, "Blue Dart", null).andExpect(status().isOk());
        ship(shipmentId, "Delhivery", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already shipped with Blue Dart (BD-77001122)")));
        pack(shipmentId).andExpect(status().isOk()); // already packed (and shipped)
        scan(shipmentId, "OUT_FOR_DELIVERY", null, null).andExpect(status().isOk());
        scan(shipmentId, "OUT_FOR_DELIVERY", null, null).andExpect(status().isOk());
        scan(shipmentId, "DELIVERED", null, null).andExpect(status().isOk());
        scan(shipmentId, "DELIVERED", null, null).andExpect(status().isOk());

        assertThat(count("SELECT COUNT(*) FROM tracking_events WHERE shipment_id = ?", shipmentId)).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM outbox_events")).isEqualTo(3); // shipped, out for delivery, delivered
    }

    @Test
    void movesTheLifecycleDoesNotAllowAreRefused() throws Exception {
        UUID shipmentId = shipmentFor(UUID.randomUUID(), customerId);
        ship(shipmentId, "Delhivery", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("isn't packed yet")));
        scan(shipmentId, "OUT_FOR_DELIVERY", null, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("hasn't been shipped yet")));
        pack(shipmentId).andExpect(status().isOk());
        packAndShip(shipmentId);
        scan(shipmentId, "DELIVERY_FAILED", null, "Nobody home").andExpect(status().isConflict()); // not out for delivery
        scan(shipmentId, "DELIVERED", null, null).andExpect(status().isOk());
        scan(shipmentId, "IN_TRANSIT", "Hub", null).andExpect(status().isConflict());
        assertThat(statusOf(shipmentId)).isEqualTo("DELIVERED");
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        UUID shipmentId = shipmentFor(UUID.randomUUID(), customerId);
        pack(shipmentId).andExpect(status().isOk());

        ship(shipmentId, "FedEx", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Carriers: Delhivery, Blue Dart, Ecom Express, India Post")));
        ship(shipmentId, " ", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.carrier").exists());
        ship(shipmentId, "Delhivery", "ab").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.trackingNumber").exists());
        packAndShip(shipmentId);

        scan(shipmentId, "PACKED", null, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERY_FAILED, DELIVERED")));
        scan(shipmentId, "LOST", null, null).andExpect(status().isBadRequest());
        scan(shipmentId, null, "Hub", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.status").exists());
        scan(shipmentId, "IN_TRANSIT", " ", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("location is required")));
        scan(shipmentId, "OUT_FOR_DELIVERY", null, null).andExpect(status().isOk());
        scan(shipmentId, "DELIVERY_FAILED", null, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("note is required")));
        scan(UUID.randomUUID(), "DELIVERED", null, null).andExpect(status().isNotFound());
        pack(UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void aTrackingNumberBelongsToOneShipment() throws Exception {
        UUID first = shipmentFor(UUID.randomUUID(), customerId);
        UUID second = shipmentFor(UUID.randomUUID(), otherCustomerId);
        pack(first).andExpect(status().isOk());
        pack(second).andExpect(status().isOk());
        ship(first, "India Post", "EE123456789IN").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.carrier").value("India Post"));
        ship(second, "India Post", "ee123456789in").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already used")));
        assertThat(statusOf(second)).isEqualTo("PACKED");
    }

    @Test
    void sixWarehouseStaffShippingTheSameParcelAtOnceShipItOnce() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID shipmentId = shipmentFor(orderId, customerId);
        pack(shipmentId).andExpect(status().isOk());

        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String number = "DLV-90000" + i;
            Callable<Integer> call = () -> ship(shipmentId, "Delhivery", number).andReturn().getResponse().getStatus();
            results.add(pool.submit(call));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsOnly(200, 409);
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM tracking_events WHERE shipment_id = ? AND status = 'SHIPPED'", shipmentId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE event_type = 'SHIPMENT_SHIPPED'")).isEqualTo(1);
        awaitRecords("shipment-events", orderId.toString(), 1);
    }

    @Test
    void outboxIsEmptiedToKafka() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID shipmentId = shipmentFor(orderId, customerId);
        packAndShip(shipmentId);
        awaitRecords("shipment-events", orderId.toString(), 1);
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> count("SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL") == 0);
    }
}
