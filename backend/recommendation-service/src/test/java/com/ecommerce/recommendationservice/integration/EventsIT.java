package com.ecommerce.recommendationservice.integration;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Events from Kafka; repeated, older and bad events. */
class EventsIT extends AbstractIntegrationTest {

    @Test
    void ordersCountOnce_andOnlyPaidOnesCanBeTakenBack() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        assertThat(purchases.apply(paid(order, UUID.randomUUID(), a, b))).isTrue();
        assertThat(purchases.apply(paid(order, UUID.randomUUID(), a, b))).isFalse();
        assertThat(count("SELECT orders FROM bought_together WHERE product_id = ? AND other_product_id = ?", a, b)).isEqualTo(1);
        assertThat(count("SELECT units FROM daily_sales WHERE product_id = ?", a)).isEqualTo(1);

        assertThat(purchases.apply(cancelled(UUID.randomUUID(), UUID.randomUUID()))).isFalse(); // never paid
        assertThat(purchases.apply(cancelled(order, UUID.randomUUID()))).isTrue();
        assertThat(purchases.apply(cancelled(order, UUID.randomUUID()))).isFalse();
        assertThat(count("SELECT orders FROM bought_together WHERE product_id = ? AND other_product_id = ?", b, a)).isZero();
        assertThat(count("SELECT units FROM daily_sales WHERE product_id = ?", a)).isZero();
        assertThatThrownBy(() -> purchases.apply(paid(UUID.randomUUID(), UUID.randomUUID()))) // no items
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void olderCatalogEventsChangeNothing() {
        UUID id = UUID.randomUUID();
        indexer.apply(product(id, "X", "New name", "B", null, "10", null, "d", "ACTIVE", 3, Instant.now()));
        assertThat(indexer.apply(product(id, "X", "Old name", "B", null, "10", null, "d", "ACTIVE", 2, Instant.now()))).isFalse();
        assertThat(indexer.apply(stock(id, 5, 2))).isTrue();
        assertThat(indexer.apply(stock(id, 9, 1))).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM products WHERE product_id = ?", String.class, id)).isEqualTo("New name");
        assertThat(count("SELECT available_quantity FROM products WHERE product_id = ?", id)).isEqualTo(5);
    }

    @Test
    void eventsArriveThroughKafka_andBadOnesAreDeadLettered() throws Exception {
        UUID cat = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        kafkaTemplate.send("category-events", cat.toString(), toJson(category("CATEGORY_CREATED", cat, "Phones", null, 0))).get();
        for (UUID id : List.of(a, b)) {
            kafkaTemplate.send("product-events", id.toString(),
                    toJson(product(id, "S-" + id, "Phone " + id, "B", cat, "100", null, "d", "ACTIVE", 0, Instant.now()))).get();
            kafkaTemplate.send("inventory-events", id.toString(), toJson(stock(id, 3, 1))).get();
            kafkaTemplate.send("review-events", id.toString(), toJson(rating(id, "4.0", 2, 1))).get();
        }
        // order-service's event carries many more fields: they are ignored
        String orderJson = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"ORDER_CONFIRMED\",\"orderId\":\"" + order
                + "\",\"orderNumber\":\"ORD-1\",\"userId\":\"" + UUID.randomUUID() + "\",\"status\":\"CONFIRMED\",\"totalAmount\":200,"
                + "\"items\":[{\"productId\":\"" + a + "\",\"sku\":\"S\",\"quantity\":1,\"unitPrice\":100},"
                + "{\"productId\":\"" + b + "\",\"sku\":\"S\",\"quantity\":2,\"unitPrice\":100}],\"orderVersion\":3}";
        kafkaTemplate.send("order-events", order.toString(), orderJson).get();
        awaitCount("SELECT COUNT(*) FROM products WHERE category_id = ? AND available_quantity = 3 AND review_count = 2", 2, cat);
        awaitCount("SELECT COUNT(*) FROM bought_together WHERE orders = 1", 2);
        assertThat(count("SELECT units FROM daily_sales WHERE product_id = ?", b)).isEqualTo(2);

        String bad = UUID.randomUUID().toString();
        kafkaTemplate.send("order-events", bad, "{broken").get();
        List<ConsumerRecord<String, String>> dead = awaitRecords("order-events.recommendation.DLT", bad, 1);
        assertThat(dead.get(0).value()).isEqualTo("{broken");
    }
}
