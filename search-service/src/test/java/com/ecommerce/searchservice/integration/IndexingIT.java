package com.ecommerce.searchservice.integration;

import com.ecommerce.searchservice.event.ProductEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Keeping the index in step with the catalog, whatever order the events arrive in. */
class IndexingIT extends AbstractIntegrationTest {

    private final UUID phones = UUID.randomUUID();
    private final Instant created = Instant.parse("2026-10-01T10:00:00Z");

    private ProductEvent pixel(UUID id, String name, String brand, long version) {
        return product(id, "PIXEL-9", name, brand, phones, "79999", null, "A phone", "ACTIVE", version, created);
    }

    @Test
    void anOlderProductEventNeverOverwritesANewerOne() throws Exception {
        UUID id = UUID.randomUUID();
        assertThat(indexer.apply(pixel(id, "Pixel 9 Pro", "Google", 3))).isTrue();
        assertThat(indexer.apply(pixel(id, "Pixel 9", "Google", 2))).isFalse();

        assertThat(names(searchData("?q=pixel"))).containsExactly("Pixel 9 Pro");
    }

    @Test
    void theSameVersionAgainFillsInWhatOlderEventsLacked() throws Exception {
        UUID id = UUID.randomUUID();
        // An event from before Phase 12: no brand, category, description or images
        indexer.apply(new ProductEvent(UUID.randomUUID(), "PRODUCT_UPDATED", created, id, "PIXEL-9", "Pixel 9", "ACTIVE",
                null, null, null, null, null, null, null, null, 4));
        getAs(adminAuth(), SEARCH_URL + "/admin/stats").andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.withoutDetails").value(1));
        assertThat(names(searchData("?q=google"))).isEmpty();

        // product-service republishes the catalog: same version, all fields
        assertThat(indexer.apply(pixel(id, "Pixel 9", "Google", 4))).isTrue();
        assertThat(names(searchData("?q=google"))).containsExactly("Pixel 9");
        assertThat(searchData("?q=pixel").at("/results/content/0/sellingPrice").decimalValue()).isEqualByComparingTo("79999");
    }

    @Test
    void stockAndRatingsMayArriveBeforeTheProduct() throws Exception {
        UUID id = UUID.randomUUID();
        indexer.apply(stock(id, 7, 2));
        indexer.apply(rating(id, "4.3", 12, 5));
        assertThat(searchData("").at("/results/totalElements").asLong()).isZero(); // nothing to show yet

        indexer.apply(pixel(id, "Pixel 9", "Google", 0));
        var hit = searchData("?q=pixel").at("/results/content/0");
        assertThat(hit.get("availableQuantity").asInt()).isEqualTo(7);
        assertThat(hit.get("averageRating").decimalValue()).isEqualByComparingTo("4.3");
        assertThat(hit.get("reviewCount").asInt()).isEqualTo(12);

        assertThat(indexer.apply(stock(id, 1, 1))).isFalse();       // older
        assertThat(indexer.apply(rating(id, "1.0", 1, 4))).isFalse(); // older
        assertThat(indexer.apply(stock(id, 0, 3))).isTrue();
        assertThat(names(searchData("?inStock=false"))).containsExactly("Pixel 9");
        assertThatThrownBy(() -> indexer.apply(rating(id, "5.5", 1, 9))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void discontinuedProductsDisappear() throws Exception {
        UUID id = UUID.randomUUID();
        indexer.apply(pixel(id, "Pixel 9", "Google", 0));
        assertThat(names(searchData("?q=pixel"))).hasSize(1);

        indexer.apply(product(id, "PIXEL-9", "Pixel 9", "Google", phones, "79999", null, "A phone", "DISCONTINUED", 1, created));
        assertThat(names(searchData("?q=pixel"))).isEmpty();
    }

    @Test
    void categoriesCanArriveLate_beRenamedMovedAndDeleted() throws Exception {
        UUID electronics = UUID.randomUUID();
        UUID home = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        indexer.apply(pixel(id, "Pixel 9", "Google", 0));                       // its category isn't known yet
        indexer.apply(category("CATEGORY_CREATED", electronics, "Electronics", null, 0));
        indexer.apply(category("CATEGORY_CREATED", home, "Home", null, 0));
        indexer.apply(category("CATEGORY_CREATED", phones, "Mobiles", electronics, 0));

        assertThat(names(searchData("?q=mobiles"))).containsExactly("Pixel 9");     // found by its category's name
        assertThat(names(searchData("?categoryId=" + electronics))).containsExactly("Pixel 9");

        indexer.apply(category("CATEGORY_UPDATED", phones, "Smartphones", home, 1)); // renamed and moved
        assertThat(indexer.apply(category("CATEGORY_UPDATED", phones, "Mobiles", electronics, 0))).isFalse(); // old
        assertThat(names(searchData("?q=smartphones"))).containsExactly("Pixel 9");
        assertThat(names(searchData("?q=mobiles"))).isEmpty();
        assertThat(names(searchData("?categoryId=" + home))).containsExactly("Pixel 9");
        assertThat(names(searchData("?categoryId=" + electronics))).isEmpty();
        assertThat(searchData("?q=pixel").at("/results/content/0/categoryName").asText()).isEqualTo("Smartphones");

        indexer.apply(category("CATEGORY_DELETED", home, "Home", null, 1));
        assertThat(count("SELECT COUNT(*) FROM categories")).isEqualTo(2);
    }

    @Test
    void aCategoryAndItsProductArrivingAtTheSameMomentAlwaysEndUpLinked() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 25; i++) {
                final int attempt = i;
                UUID categoryId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();
                java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
                var c = pool.submit(() -> {
                    start.await();
                    return indexer.apply(category("CATEGORY_CREATED", categoryId, "Wearables", null, 0));
                });
                var p = pool.submit(() -> {
                    start.await();
                    return indexer.apply(product(productId, "W-" + attempt, "Watch " + attempt, "Google", categoryId, "999", null,
                            "Watch", "ACTIVE", 0, created));
                });
                start.countDown();
                c.get();
                p.get();
                assertThat(jdbcTemplate.queryForObject("SELECT category_name FROM search_products WHERE product_id = ?",
                        String.class, productId)).as("attempt %d", i).isEqualTo("Wearables");
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void eventsArriveThroughKafka_andBadOnesAreDeadLettered() throws Exception {
        UUID categoryId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        kafkaTemplate.send("category-events", categoryId.toString(),
                toJson(category("CATEGORY_CREATED", categoryId, "Wearables", null, 0))).get();
        kafkaTemplate.send("product-events", id.toString(), toJson(product(id, "WATCH-1", "Pixel Watch", "Google",
                categoryId, "29999", null, "Smart watch", "ACTIVE", 0, created))).get();
        kafkaTemplate.send("inventory-events", id.toString(), toJson(stock(id, 4, 1))).get();
        kafkaTemplate.send("review-events", id.toString(), toJson(rating(id, "4.6", 9, 1))).get();
        awaitCount("SELECT COUNT(*) FROM search_products WHERE product_id = ? AND category_name = 'Wearables' "
                + "AND available_quantity = 4 AND review_count = 9", 1, id);
        assertThat(names(searchData("?q=wearables"))).containsExactly("Pixel Watch");

        String brokenKey = UUID.randomUUID().toString();
        kafkaTemplate.send("product-events", brokenKey, "{not json").get();
        UUID nameless = UUID.randomUUID();
        kafkaTemplate.send("product-events", nameless.toString(), toJson(new ProductEvent(UUID.randomUUID(),
                "PRODUCT_CREATED", created, nameless, "SKU", null, "ACTIVE", null, null, null, null, null, null, null, null, 0))).get();
        kafkaTemplate.send("product-events", UUID.randomUUID().toString(), "{\"eventType\":\"PRODUCT_TELEPORTED\"}").get();

        List<ConsumerRecord<String, String>> dead = awaitRecords("product-events.search.DLT", brokenKey, 1);
        assertThat(dead.get(0).value()).isEqualTo("{not json");
        awaitRecords("product-events.search.DLT", nameless.toString(), 1);

        UUID next = UUID.randomUUID(); // the topic keeps flowing
        kafkaTemplate.send("product-events", next.toString(), toJson(product(next, "WATCH-2", "Pixel Watch 2", "Google",
                categoryId, "34999", null, "Smart watch", "ACTIVE", 0, created))).get();
        awaitCount("SELECT COUNT(*) FROM search_products WHERE product_id = ?", 1, next);
    }
}
