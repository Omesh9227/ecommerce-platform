package com.ecommerce.adminservice.integration;

import com.ecommerce.adminservice.event.OrderEvent;
import com.ecommerce.adminservice.event.PaymentEvent;
import com.ecommerce.adminservice.event.ShipmentEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Events from Kafka (as the other services really send them); repeated, older, unknown and bad events. */
class EventsIT extends AbstractIntegrationTest {

    @Test
    void eachEventCountsOnce() {
        Order o = new Order(UUID.randomUUID(), "700", item(UUID.randomUUID(), "Lamp", 1, "700"));
        placed(o, "2026-10-24T05:00:00Z");
        PaymentEvent failed = new PaymentEvent(UUID.randomUUID(), "PAYMENT_FAILED", Instant.parse("2026-10-24T05:01:00Z"), o.id,
                o.number, o.total, "INR", "CARD", "Insufficient funds");
        assertThat(recorder.apply(failed)).isTrue();
        assertThat(recorder.apply(failed)).isFalse(); // payment-service re-sent it
        ShipmentEvent missed = missedDelivery(o, "2026-10-24T09:00:00Z");
        assertThat(recorder.apply(missed)).isTrue();
        assertThat(recorder.apply(missed)).isFalse();
        assertThat(count("SELECT COUNT(*) FROM payment_events")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM delivery_failures")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM activity")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM order_items")).isEqualTo(1);
    }

    @Test
    void stepsArrivingLateKeepTheirTimes_butNotTheirStatus() {
        Order o = new Order(UUID.randomUUID(), "700", item(UUID.randomUUID(), "Lamp", 1, "700"));
        OrderEvent placedEvent = o.event("ORDER_PLACED", "PENDING_PAYMENT", "2026-10-24T05:00:00Z", null);
        OrderEvent confirmed = o.event("ORDER_CONFIRMED", "CONFIRMED", "2026-10-24T05:05:00Z", null);
        assertThat(recorder.apply(confirmed)).isTrue();
        assertThat(recorder.apply(placedEvent)).isFalse(); // older version: skipped
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, o.id)).isEqualTo("CONFIRMED");
        assertThat(count("SELECT COUNT(*) FROM orders WHERE paid_at IS NOT NULL")).isEqualTo(1);
    }

    @Test
    void olderStockChangesNothing_andOnlyLowStockIsInTheFeed() {
        UUID lamp = UUID.randomUUID();
        assertThat(recorder.apply(stock(lamp, "Lamp", 2, 5, "INVENTORY_LOW", 7, "2026-10-24T05:00:00Z"))).isTrue();
        assertThat(recorder.apply(stock(lamp, "Lamp", 40, 5, "INVENTORY_UPDATED", 6, "2026-10-24T04:00:00Z"))).isFalse();
        assertThat(count("SELECT available_quantity FROM stock_levels WHERE product_id = ?", lamp)).isEqualTo(2);
        assertThat(recorder.apply(stock(lamp, "Lamp", 40, 5, "INVENTORY_UPDATED", 8, "2026-10-24T06:00:00Z"))).isTrue();
        assertThat(count("SELECT COUNT(*) FROM stock_levels WHERE low_stock")).isZero();
        assertThat(count("SELECT COUNT(*) FROM activity")).isEqualTo(1);
    }

    @Test
    void lowStockAlert_afterTheStockUpdateOfTheSameChange() {
        // What inventory-service really sends when stock falls below the threshold: two events, same version
        UUID lamp = UUID.randomUUID();
        var low = stock(lamp, "Lamp", 4, 5, "INVENTORY_LOW", 3, "2026-10-24T05:00:00Z");
        assertThat(recorder.apply(stock(lamp, "Lamp", 4, 5, "INVENTORY_UPDATED", 3, "2026-10-24T05:00:00Z"))).isTrue();
        assertThat(recorder.apply(low)).isTrue();
        assertThat(recorder.apply(low)).isFalse(); // the same alert again
        assertThat(jdbcTemplate.queryForList("SELECT kind FROM activity", String.class)).containsExactly("LOW_STOCK");
        // An alert that arrives after the product was restocked is out of date
        recorder.apply(stock(lamp, "Lamp", 50, 5, "INVENTORY_UPDATED", 5, "2026-10-24T06:00:00Z"));
        assertThat(recorder.apply(stock(lamp, "Lamp", 3, 5, "INVENTORY_LOW", 4, "2026-10-24T05:30:00Z"))).isFalse();
        assertThat(count("SELECT COUNT(*) FROM activity")).isEqualTo(1);
    }

    @Test
    void otherEventTypesAreIgnored_andIncompleteOnesRejected() {
        Order o = new Order(UUID.randomUUID(), "700", item(UUID.randomUUID(), "Lamp", 1, "700"));
        assertThat(recorder.apply(o.event("ORDER_SOMETHING_NEW", "CONFIRMED", "2026-10-24T05:00:00Z", null))).isFalse();
        assertThat(recorder.apply(new ShipmentEvent(UUID.randomUUID(), "SHIPMENT_SHIPPED", Instant.now(), o.id, o.number,
                "Delhivery", "EC1", null, 0))).isFalse();
        assertThat(count("SELECT COUNT(*) FROM orders")).isZero();
        assertThatThrownBy(() -> recorder.apply(new OrderEvent(UUID.randomUUID(), "ORDER_PLACED", Instant.now(), UUID.randomUUID(),
                null, UUID.randomUUID(), "PENDING_PAYMENT", null, BigDecimal.TEN, "INR", List.of(), 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theFeedKeepsOnlyRecentDays() {
        clock.set(Instant.parse("2026-10-24T12:00:00Z"));
        Order old = new Order(UUID.randomUUID(), "100", item(UUID.randomUUID(), "Pen", 1, "100"));
        placed(old, "2026-07-01T05:00:00Z");
        paid(old, "2026-07-01T05:05:00Z");
        Order recent = new Order(UUID.randomUUID(), "100", item(UUID.randomUUID(), "Pen", 1, "100"));
        placed(recent, "2026-10-24T05:00:00Z");
        paid(recent, "2026-10-24T05:05:00Z");
        assertThat(jdbcTemplate.queryForList("SELECT order_number FROM activity", String.class)).containsExactly(recent.number);
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(2); // the report data stays
    }

    @Test
    void eventsArriveThroughKafka_andBadOnesAreDeadLettered() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        // The services' events carry more fields than the dashboard needs: they are ignored
        String item = "{\"productId\":\"" + productId + "\",\"sku\":\"PIX-9\",\"productName\":\"Pixel 9\",\"quantity\":2,\"unitPrice\":900.00}";
        String address = "{\"fullName\":\"Omesh\",\"phone\":\"9999999999\",\"line1\":\"1 MG Road\",\"city\":\"Bengaluru\","
                + "\"state\":\"KA\",\"postalCode\":\"560001\",\"country\":\"IN\"}";
        for (String[] step : new String[][]{{"ORDER_PLACED", "PENDING_PAYMENT", "1"}, {"ORDER_CONFIRMED", "CONFIRMED", "2"},
                {"ORDER_SHIPPED", "SHIPPED", "3"}}) {
            kafkaTemplate.send("order-events", orderId.toString(), "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\""
                    + step[0] + "\",\"occurredAt\":\"2026-10-24T05:0" + step[2] + ":00Z\",\"orderId\":\"" + orderId
                    + "\",\"orderNumber\":\"ORD-KAFKA\",\"userId\":\"" + userId + "\",\"userEmail\":\"omesh@example.com\","
                    + "\"status\":\"" + step[1] + "\",\"reason\":null,\"carrier\":null,\"trackingNumber\":null,"
                    + "\"estimatedDeliveryDate\":\"2026-10-29\",\"totalAmount\":1800.00,\"currency\":\"INR\",\"items\":[" + item
                    + "],\"shippingAddress\":" + address + ",\"orderVersion\":" + step[2] + "}").get();
        }
        kafkaTemplate.send("payment-events", orderId.toString(), "{\"eventId\":\"" + UUID.randomUUID()
                + "\",\"eventType\":\"PAYMENT_FAILED\",\"occurredAt\":\"2026-10-24T05:01:30Z\",\"paymentId\":\"" + UUID.randomUUID()
                + "\",\"orderId\":\"" + orderId + "\",\"orderNumber\":\"ORD-KAFKA\",\"userId\":\"" + userId
                + "\",\"status\":\"FAILED\",\"amount\":1800.00,\"currency\":\"INR\",\"method\":\"CARD\",\"cardBrand\":\"VISA\","
                + "\"cardLast4\":\"0002\",\"reason\":\"Your card was declined.\",\"paymentVersion\":1}").get();
        for (String type : List.of("INVENTORY_UPDATED", "INVENTORY_LOW")) {
            kafkaTemplate.send("inventory-events", productId.toString(), "{\"eventId\":\"" + UUID.randomUUID()
                + "\",\"eventType\":\"" + type + "\",\"occurredAt\":\"2026-10-24T05:02:30Z\",\"productId\":\"" + productId
                + "\",\"sku\":\"PIX-9\",\"productName\":\"Pixel 9\",\"quantityOnHand\":4,\"quantityReserved\":2,"
                + "\"availableQuantity\":2,\"lowStockThreshold\":5,\"lowStock\":true,\"inventoryVersion\":9}").get();
        }
        kafkaTemplate.send("shipment-events", orderId.toString(), "{\"eventId\":\"" + UUID.randomUUID()
                + "\",\"eventType\":\"SHIPMENT_DELIVERY_FAILED\",\"occurredAt\":\"2026-10-24T09:00:00Z\",\"shipmentId\":\""
                + UUID.randomUUID() + "\",\"orderId\":\"" + orderId + "\",\"orderNumber\":\"ORD-KAFKA\",\"userId\":\"" + userId
                + "\",\"userEmail\":\"omesh@example.com\",\"status\":\"DELIVERY_FAILED\",\"carrier\":\"Blue Dart\","
                + "\"trackingNumber\":\"EC123456789012\",\"estimatedDeliveryDate\":\"2026-10-29\",\"location\":null,"
                + "\"note\":\"Address locked\",\"failedAttempts\":1,\"shipmentVersion\":5}").get();

        awaitCount("SELECT COUNT(*) FROM orders WHERE status = 'SHIPPED' AND paid_at IS NOT NULL AND item_count = 2", 1);
        awaitCount("SELECT COUNT(*) FROM activity", 5); // paid, shipped, payment failed, low stock, missed delivery
        assertThat(count("SELECT available_quantity FROM stock_levels WHERE product_id = ?", productId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT message FROM activity WHERE kind = 'DELIVERY_FAILED'", String.class))
                .isEqualTo("Delivery of ORD-KAFKA failed (Blue Dart): Address locked");
        assertThat(jdbcTemplate.queryForObject("SELECT message FROM activity WHERE kind = 'ORDER_PAID'", String.class))
                .isEqualTo("Order ORD-KAFKA paid: INR 1,800.00");

        String broken = UUID.randomUUID().toString();
        kafkaTemplate.send("payment-events", broken, "{broken").get();
        assertThat(awaitRecords("payment-events.admin.DLT", broken, 1).get(0).value()).isEqualTo("{broken");
        String incomplete = UUID.randomUUID().toString();
        String noNumber = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"ORDER_PLACED\",\"orderId\":\"" + incomplete
                + "\",\"status\":\"PENDING_PAYMENT\",\"totalAmount\":1,\"orderVersion\":1}";
        kafkaTemplate.send("order-events", incomplete, noNumber).get();
        List<ConsumerRecord<String, String>> dead = awaitRecords("order-events.admin.DLT", incomplete, 1);
        assertThat(dead.get(0).value()).isEqualTo(noNumber);
    }
}
