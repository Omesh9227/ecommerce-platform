package com.ecommerce.reviewservice.integration;

import com.ecommerce.reviewservice.service.PurchaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ORDER_DELIVERED (order-events) makes the customer's reviews of those products "verified purchase". */
class VerifiedPurchaseIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private PurchaseService purchaseService;

    @Test
    void deliveryOnKafka_makesAnExistingReviewVerified() throws Exception {
        UUID phone = stub.product("PIXEL-8");
        UUID id = reviewId(customer, phone, 5);

        kafkaTemplate.send("order-events", UUID.randomUUID().toString(), toJson(delivered(customerId, phone)))
                .get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).until(() ->
                count("SELECT COUNT(*) FROM reviews WHERE id = ? AND verified_purchase", id) == 1);
    }

    @Test
    void reviewAfterDelivery_isVerified_onlyForThatCustomerAndProduct() throws Exception {
        UUID phone = stub.product("PIXEL-8");
        UUID case_ = stub.product("CASE");
        purchaseService.onOrderEvent(delivered(customerId, phone));

        review(customer, phone, 5, "Received it", null).andExpect(jsonPath("$.data.verifiedPurchase").value(true));
        review(customer, case_, 5, "Never bought it", null).andExpect(jsonPath("$.data.verifiedPurchase").value(false));
        review(otherCustomer, phone, 5, "Someone else bought it", null)
                .andExpect(jsonPath("$.data.verifiedPurchase").value(false));
    }

    @Test
    void otherOrderEvents_areIgnored_andRepeatedDeliveriesRecordedOnce() {
        UUID phone = stub.product("PIXEL-8");
        var placed = new com.ecommerce.reviewservice.event.OrderEvent(UUID.randomUUID(), "ORDER_PLACED", null,
                UUID.randomUUID(), "ORD-0000000000AA", customerId, delivered(customerId, phone).items());

        purchaseService.onOrderEvent(placed);
        assertThat(count("SELECT COUNT(*) FROM purchases")).isZero();

        purchaseService.onOrderEvent(delivered(customerId, phone));
        purchaseService.onOrderEvent(delivered(customerId, phone));
        assertThat(count("SELECT COUNT(*) FROM purchases WHERE user_id = ?", customerId)).isEqualTo(1);
    }

    @Test
    void malformedOrderEvent_goesToTheReviewDeadLetterTopic() throws Exception {
        String key = UUID.randomUUID().toString();

        kafkaTemplate.send("order-events", key, "not json at all").get(10, TimeUnit.SECONDS);

        assertThat(awaitRecords("order-events.review.DLT", key, 1).get(0).value()).isEqualTo("not json at all");
    }

    @Test
    void verifiedReviews_canBeListedOnTheirOwn() throws Exception {
        UUID phone = stub.product("PIXEL-8");
        purchaseService.onOrderEvent(delivered(customerId, phone));
        reviewId(customer, phone, 5);
        reviewId(otherCustomer, phone, 2);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(REVIEWS_URL + "/products/" + phone).param("verifiedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].rating").value(5));
    }
}
