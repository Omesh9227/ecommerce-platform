package com.ecommerce.shippingservice.integration;

import com.ecommerce.shippingservice.event.OrderEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** order-events -> shipments: created when an order is paid, cancelled when it is cancelled. */
class OrderEventsIT extends AbstractIntegrationTest {

    @Test
    void paidOrderBecomesShipmentWaitingToBePackedWithDeliveryEstimate() throws Exception {
        clock.set(Instant.parse("2026-10-24T06:00:00Z"));
        UUID orderId = UUID.randomUUID();
        UUID shipmentId = shipmentFor(orderId, customerId);

        JsonNode s = readJson(getAs(customer, SHIPMENTS_URL + "/orders/" + orderNumber(orderId))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(s.get("id").asText()).isEqualTo(shipmentId.toString());
        assertThat(s.get("status").asText()).isEqualTo("PENDING");
        assertThat(s.get("estimatedDeliveryDate").asText()).isEqualTo("2026-10-25"); // Bengaluru -> Bengaluru: 1 day
        assertThat(s.at("/deliveryAddress/recipientName").asText()).isEqualTo("Omesh Kumar");
        assertThat(s.at("/deliveryAddress/postalCode").asText()).isEqualTo("560001");
        assertThat(s.get("items")).hasSize(2);
        assertThat(s.get("tracking")).hasSize(1);
        assertThat(s.at("/tracking/0/description").asText()).isEqualTo("Order confirmed - preparing your package");
        assertThat(s.has("carrier")).isFalse();
        assertThat(s.has("userId")).isFalse();
    }

    @Test
    void farAwayAddressGetsALongerEstimate() throws Exception {
        clock.set(Instant.parse("2026-10-24T06:00:00Z"));
        UUID orderId = UUID.randomUUID();
        sendOrderEvent(confirmed(orderId, customerId, address("New Delhi", "Delhi", "110001")));
        awaitShipment(orderId);
        assertThat(jdbcTemplate.queryForObject("SELECT estimated_delivery_date::text FROM shipments WHERE order_id = ?",
                String.class, orderId)).isEqualTo("2026-10-29");
    }

    @Test
    void sameEventTwiceCreatesOneShipment() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderEvent event = confirmed(orderId, customerId);
        sendOrderEvent(event);
        sendOrderEvent(event);
        UUID marker = UUID.randomUUID();
        sendOrderEvent(confirmed(marker, customerId)); // processed after both (same partition)
        awaitShipment(marker);

        assertThat(count("SELECT COUNT(*) FROM shipments WHERE order_id = ?", orderId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM tracking_events t JOIN shipments s ON s.id = t.shipment_id WHERE s.order_id = ?",
                orderId)).isEqualTo(1);
    }

    @Test
    void otherOrderEventsAndOldEventsWithoutAddressAreSkipped() throws Exception {
        UUID placed = UUID.randomUUID();
        OrderEvent p = confirmed(placed, customerId);
        sendOrderEvent(new OrderEvent(p.eventId(), "ORDER_PLACED", p.occurredAt(), placed, p.orderNumber(), customerId,
                p.userEmail(), null, p.items(), p.shippingAddress(), 1));
        UUID legacy = UUID.randomUUID();
        OrderEvent l = confirmed(legacy, customerId);
        sendOrderEvent(new OrderEvent(l.eventId(), l.eventType(), l.occurredAt(), legacy, l.orderNumber(), customerId,
                l.userEmail(), null, l.items(), null, 3));
        UUID marker = UUID.randomUUID();
        sendOrderEvent(confirmed(marker, customerId));
        awaitShipment(marker);

        assertThat(count("SELECT COUNT(*) FROM shipments")).isEqualTo(1);
    }

    @Test
    void malformedAndIncompleteEventsGoToTheDeadLetterTopic() throws Exception {
        String malformedKey = UUID.randomUUID().toString();
        sendRaw(malformedKey, "{not json");
        UUID incomplete = UUID.randomUUID();
        sendOrderEvent(confirmed(incomplete, customerId, address("Bengaluru", "Karnataka", "12")));

        List<ConsumerRecord<String, String>> dead = awaitRecords("order-events.shipping.DLT", malformedKey, 1);
        assertThat(dead.get(0).value()).isEqualTo("{not json");
        awaitRecords("order-events.shipping.DLT", incomplete.toString(), 1);
        assertThat(count("SELECT COUNT(*) FROM shipments WHERE order_id = ?", incomplete)).isZero();

        UUID next = UUID.randomUUID(); // the partition keeps going
        sendOrderEvent(confirmed(next, customerId));
        awaitShipment(next);
    }

    @Test
    void cancelledOrderCancelsAShipmentStillInTheWarehouse() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID shipmentId = shipmentFor(orderId, customerId);
        pack(shipmentId).andExpect(status().isOk());

        sendOrderEvent(cancelled(orderId, customerId, "Cancelled by customer - a full refund has been requested"));
        await().atMost(Duration.ofSeconds(20)).until(() -> "CANCELLED".equals(statusOf(shipmentId)));

        getAs(customer, SHIPMENTS_URL + "/orders/" + orderNumber(orderId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusReason").value("Cancelled by customer - a full refund has been requested"))
                .andExpect(jsonPath("$.data.cancelledAt").exists())
                .andExpect(jsonPath("$.data.tracking.length()").value(3));
        pack(shipmentId).andExpect(status().isConflict());
        ship(shipmentId, "Delhivery", null).andExpect(status().isConflict());
        // nothing is announced for a cancellation (the order itself was cancelled)
        assertThat(count("SELECT COUNT(*) FROM outbox_events")).isZero();
    }

    @Test
    void orderCancelledAfterDispatchLeavesOneRecallNote() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID shipmentId = shipmentFor(orderId, customerId);
        packAndShip(shipmentId);

        sendOrderEvent(cancelled(orderId, customerId, "Cancelled by customer"));
        sendOrderEvent(cancelled(orderId, customerId, "Cancelled by customer"));
        UUID marker = UUID.randomUUID();
        sendOrderEvent(confirmed(marker, customerId));
        awaitShipment(marker);

        assertThat(statusOf(shipmentId)).isEqualTo("SHIPPED");
        assertThat(count("SELECT COUNT(*) FROM tracking_events WHERE shipment_id = ? AND description LIKE 'The order was cancelled after%'",
                shipmentId)).isEqualTo(1);
    }

    @Test
    void cancellingAnUnpaidOrderChangesNothing() throws Exception {
        sendOrderEvent(cancelled(UUID.randomUUID(), customerId, "Payment was not received in time"));
        UUID marker = UUID.randomUUID();
        sendOrderEvent(confirmed(marker, customerId));
        awaitShipment(marker);
        assertThat(count("SELECT COUNT(*) FROM shipments")).isEqualTo(1);
    }
}
