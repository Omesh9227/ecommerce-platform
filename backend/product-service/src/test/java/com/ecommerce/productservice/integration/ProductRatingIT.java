package com.ecommerce.productservice.integration;

import com.ecommerce.productservice.event.EventTypes;
import com.ecommerce.productservice.event.ReviewEvent;
import com.ecommerce.productservice.service.ProductRatingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRODUCT_RATING_UPDATED events from review-service update each product's averageRating / reviewCount. */
class ProductRatingIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ProductRatingService ratingService;

    private static String ratingUpdatedJson(UUID productId, String average, int count, long version) {
        // Same shape review-service publishes (ratingCounts etc. are ignored by product-service)
        return """
                {"eventId":"%s","eventType":"PRODUCT_RATING_UPDATED","occurredAt":"2026-10-24T10:00:00Z",
                 "productId":"%s","averageRating":%s,"reviewCount":%d,"ratingCounts":{"5":1,"4":0,"3":0,"2":0,"1":0},
                 "ratingVersion":%d}
                """.formatted(UUID.randomUUID(), productId, average, count, version);
    }

    private static ReviewEvent rating(UUID productId, String average, int count, long version) {
        return new ReviewEvent(UUID.randomUUID(), EventTypes.PRODUCT_RATING_UPDATED, productId, new BigDecimal(average),
                count, version);
    }

    @Test
    void newProduct_hasNoRating_untilReviewServiceReportsOne() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        UUID id = createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", null, null));

        mockMvc.perform(get(PRODUCTS_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(0.0))
                .andExpect(jsonPath("$.data.reviewCount").value(0));

        kafkaTemplate.send("review-events", id.toString(), ratingUpdatedJson(id, "4.3", 12, 7)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mockMvc.perform(get(PRODUCTS_URL + "/" + id))
                        .andExpect(jsonPath("$.data.averageRating").value(4.3))
                        .andExpect(jsonPath("$.data.reviewCount").value(12)));
    }

    @Test
    void outdatedRatingEvent_isIgnored() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        UUID id = createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", null, null));

        ratingService.apply(rating(id, "4.5", 2, 5));
        ratingService.apply(rating(id, "1.0", 1, 4));
        ratingService.apply(rating(id, "1.0", 1, 5));

        mockMvc.perform(get(PRODUCTS_URL + "/" + id))
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.reviewCount").value(2));
    }

    @Test
    void adminEdit_keepsTheRating() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        UUID id = createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", null, null));
        ratingService.apply(rating(id, "3.8", 5, 1));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(PRODUCTS_URL + "/" + id)
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(productRequest("PIXEL-8", "Pixel 8 (2024)", "Google", mobiles, "54999", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(3.8))
                .andExpect(jsonPath("$.data.reviewCount").value(5));
    }

    @Test
    void products_canBeSortedByRating() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        UUID ok = createProduct(productRequest("OK-PHONE", "Okay phone", "Acme", mobiles, "1000", null, null));
        UUID best = createProduct(productRequest("BEST-PHONE", "Best phone", "Acme", mobiles, "1000", null, null));
        UUID none = createProduct(productRequest("NEW-PHONE", "New phone", "Acme", mobiles, "1000", null, null));
        ratingService.apply(rating(ok, "3.1", 40, 1));
        ratingService.apply(rating(best, "4.8", 10, 1));

        mockMvc.perform(get(PRODUCTS_URL).param("sort", "averageRating,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(best.toString()))
                .andExpect(jsonPath("$.data.content[1].id").value(ok.toString()))
                .andExpect(jsonPath("$.data.content[2].id").value(none.toString()));
        mockMvc.perform(get(PRODUCTS_URL).param("sort", "reviewCount,desc"))
                .andExpect(jsonPath("$.data.content[0].id").value(ok.toString()));
    }

    @Test
    void invalidRating_isRejected_soItGoesToTheDeadLetterTopic() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> ratingService.apply(rating(id, "7.5", 1, 1))).isInstanceOf(IllegalArgumentException.class);
    }
}
