package com.ecommerce.reviewservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Every change publishes the product's new rating on review-events; simultaneous reviews are all counted. */
class RatingEventsIT extends AbstractIntegrationTest {

    @Test
    void ratingUpdates_reachKafka_withGrowingVersions() throws Exception {
        UUID phone = stub.product("PIXEL-8");
        reviewId(customer, phone, 5);
        reviewId(otherCustomer, phone, 2);

        List<ConsumerRecord<String, String>> records = awaitRecords("review-events", phone.toString(), 2);

        JsonNode first = readTree(records.get(0).value());
        JsonNode second = readTree(records.get(1).value());
        assertThat(first.get("eventType").asText()).isEqualTo("PRODUCT_RATING_UPDATED");
        assertThat(first.get("productId").asText()).isEqualTo(phone.toString());
        assertThat(first.get("averageRating").decimalValue()).isEqualByComparingTo("5.0");
        assertThat(second.get("averageRating").decimalValue()).isEqualByComparingTo("3.5");
        assertThat(second.get("reviewCount").asInt()).isEqualTo(2);
        assertThat(second.get("ratingCounts").get("2").asInt()).isEqualTo(1);
        assertThat(second.get("ratingVersion").asLong()).isGreaterThan(first.get("ratingVersion").asLong());
    }

    @Test
    void simultaneousReviews_areAllCounted() throws Exception {
        UUID phone = stub.product("PIXEL-8");
        List<Callable<MvcResult>> reviews = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int stars = i % 5 + 1; // two of each: average 3.0
            String token = newCustomerToken();
            reviews.add(() -> review(token, phone, stars, "Review", null).andReturn());
        }

        assertThat(runAll(reviews)).containsOnly(201);

        JsonNode summary = summary(phone);
        assertThat(summary.get("reviewCount").asInt()).isEqualTo(10);
        assertThat(summary.get("averageRating").decimalValue()).isEqualByComparingTo("3.0");
        assertThat(count("SELECT rating_version FROM product_ratings WHERE product_id = ?", phone)).isEqualTo(10);
    }

    @Test
    void doubleSubmit_storesOneReview() throws Exception {
        UUID phone = stub.product("PIXEL-8");
        List<Callable<MvcResult>> submits = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            submits.add(() -> review(customer, phone, 5, "Clicked twice", null).andReturn());
        }

        List<Integer> statuses = runAll(submits);

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(5);
        assertThat(summary(phone).get("reviewCount").asInt()).isEqualTo(1);
    }

    private static List<Integer> runAll(List<Callable<MvcResult>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Integer> statuses = new ArrayList<>();
            for (Future<MvcResult> future : pool.invokeAll(calls)) {
                statuses.add(future.get().getResponse().getStatus());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
