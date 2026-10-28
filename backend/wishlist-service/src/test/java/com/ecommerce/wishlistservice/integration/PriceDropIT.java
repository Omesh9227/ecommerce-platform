package com.ecommerce.wishlistservice.integration;

import com.ecommerce.wishlistservice.event.EventTypes;
import com.ecommerce.wishlistservice.event.ProductEvent;
import com.ecommerce.wishlistservice.service.PriceDropService;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Price drops on product-events become WISHLIST_PRICE_DROPPED on wishlist-events - once per new low price. */
class PriceDropIT extends AbstractIntegrationTest {

    @Autowired
    private PriceDropService priceDropService;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private UUID savedProduct(String sellingPrice) throws Exception {
        UUID productId = stub.product("PIXEL-8", "2000", sellingPrice, 5);
        add(customerAuth(), productId).andExpect(status().isCreated());
        return productId;
    }

    private int alerts() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type = ?", Integer.class, EventTypes.WISHLIST_PRICE_DROPPED);
        return count == null ? 0 : count;
    }

    @Test
    void priceDropOnKafka_sendsAWishlistEventForTheCustomer() throws Exception {
        UUID productId = savedProduct("1800");

        ProductEvent event = new ProductEvent(UUID.randomUUID(), EventTypes.PRODUCT_UPDATED, productId, "PIXEL-8",
                "Pixel 8 (2024)", "ACTIVE", new BigDecimal("2000"), new BigDecimal("1500"), 7);
        kafkaTemplate.send("product-events", productId.toString(), toJson(event)).get(10, TimeUnit.SECONDS);

        List<ConsumerRecord<String, String>> records = awaitRecords("wishlist-events", customerId.toString(), 1);
        JsonNode alert = readTree(records.get(0).value());
        assertThat(alert.get("eventType").asText()).isEqualTo("WISHLIST_PRICE_DROPPED");
        assertThat(alert.get("userId").asText()).isEqualTo(customerId.toString());
        assertThat(alert.get("userEmail").asText()).isEqualTo(CUSTOMER_EMAIL);
        assertThat(alert.get("productId").asText()).isEqualTo(productId.toString());
        assertThat(alert.get("productName").asText()).isEqualTo("Pixel 8 (2024)");
        assertThat(alert.get("priceWhenAdded").decimalValue()).isEqualByComparingTo("1800");
        assertThat(alert.get("previousPrice").decimalValue()).isEqualByComparingTo("1800");
        assertThat(alert.get("newPrice").decimalValue()).isEqualByComparingTo("1500");
        assertThat(alert.get("currency").asText()).isEqualTo("INR");
    }

    @Test
    void smallDrop_belowTheThreshold_sendsNothing() throws Exception {
        UUID productId = savedProduct("1800");

        // 1800 -> 1720 is 4.4 %, the threshold is 5 %
        assertThat(priceDropService.onProductEvent(productUpdated(productId, "1720", 1))).isZero();
        // 1800 -> 1710 is exactly 5 %
        assertThat(priceDropService.onProductEvent(productUpdated(productId, "1710", 2))).isEqualTo(1);
    }

    @Test
    void eachEmail_isAboutANewLowPrice_noRepeats() throws Exception {
        UUID productId = savedProduct("1800");

        assertThat(priceDropService.onProductEvent(productUpdated(productId, "1500", 1))).isEqualTo(1);
        // Up again, then back to the same low: the customer already knows about 1500
        assertThat(priceDropService.onProductEvent(productUpdated(productId, "1700", 2))).isZero();
        assertThat(priceDropService.onProductEvent(productUpdated(productId, "1500", 3))).isZero();
        // A new low, at least 5 % below 1500
        assertThat(priceDropService.onProductEvent(productUpdated(productId, "1400", 4))).isEqualTo(1);

        assertThat(alerts()).isEqualTo(2);
        BigDecimal lastAlerted = jdbcTemplate.queryForObject(
                "SELECT last_alerted_price FROM wishlist_items WHERE product_id = ?", BigDecimal.class, productId);
        assertThat(lastAlerted).isEqualByComparingTo("1400");
    }

    @Test
    void oldOrRepeatedEvents_areIgnored() throws Exception {
        UUID productId = savedProduct("1800");
        ProductEvent drop = productUpdated(productId, "1500", 5);

        assertThat(priceDropService.onProductEvent(drop)).isEqualTo(1);
        assertThat(priceDropService.onProductEvent(drop)).isZero();                                  // delivered twice
        assertThat(priceDropService.onProductEvent(productUpdated(productId, "1000", 4))).isZero();  // older version

        assertThat(alerts()).isEqualTo(1);
        Long version = jdbcTemplate.queryForObject(
                "SELECT product_version FROM product_prices WHERE product_id = ?", Long.class, productId);
        assertThat(version).isEqualTo(5L);
    }

    @Test
    void productNotForSale_sendsNothing() throws Exception {
        UUID productId = savedProduct("1800");

        ProductEvent inactive = new ProductEvent(UUID.randomUUID(), EventTypes.PRODUCT_UPDATED, productId, "PIXEL-8",
                "Pixel 8", "INACTIVE", new BigDecimal("1000"), new BigDecimal("1000"), 1);

        assertThat(priceDropService.onProductEvent(inactive)).isZero();
    }

    @Test
    void eventWithoutPrice_isAccepted_andSendsNothing() throws Exception {
        UUID productId = savedProduct("1800");

        // Events written before product-service sent prices
        assertThat(priceDropService.onProductEvent(productUpdated(productId, null, 1))).isZero();
    }

    @Test
    void everyCustomerWhoSavedIt_isTold() throws Exception {
        UUID productId = savedProduct("1800");
        add(otherCustomerAuth(), productId).andExpect(status().isCreated());

        assertThat(priceDropService.onProductEvent(productUpdated(productId, "1500", 1))).isEqualTo(2);

        List<String> keys = jdbcTemplate.queryForList(
                "SELECT event_key FROM outbox_events WHERE event_type = ? ORDER BY event_key", String.class,
                EventTypes.WISHLIST_PRICE_DROPPED);
        assertThat(keys).containsExactlyInAnyOrder(customerId.toString(), otherCustomerId.toString());
    }

    @Test
    void productNobodySaved_isOnlyRemembered() {
        UUID productId = UUID.randomUUID();

        assertThat(priceDropService.onProductEvent(productUpdated(productId, "999", 3))).isZero();

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_prices WHERE product_id = ?",
                Integer.class, productId)).isEqualTo(1);
        assertThat(alerts()).isZero();
    }

    @Test
    void malformedEvent_goesToTheWishlistDeadLetterTopic() throws Exception {
        String key = UUID.randomUUID().toString();

        kafkaTemplate.send("product-events", key, "{ this is not json").get(10, TimeUnit.SECONDS);

        List<ConsumerRecord<String, String>> dead = awaitRecords("product-events.wishlist.DLT", key, 1);
        assertThat(dead.get(0).value()).isEqualTo("{ this is not json");
    }

    @Test
    void eventsOnKafka_updateTheKnownPrice() throws Exception {
        UUID productId = UUID.randomUUID();

        kafkaTemplate.send("product-events", productId.toString(), toJson(productUpdated(productId, "1234", 9)))
                .get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbcTemplate.queryForList(
                "SELECT selling_price FROM product_prices WHERE product_id = ?", BigDecimal.class, productId))
                .singleElement().satisfies(price -> assertThat(price).isEqualByComparingTo("1234")));
    }
}
