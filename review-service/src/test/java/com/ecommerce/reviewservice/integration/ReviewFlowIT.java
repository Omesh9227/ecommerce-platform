package com.ecommerce.reviewservice.integration;

import com.ecommerce.reviewservice.support.StubServices.Service;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Writing, reading, editing and deleting reviews, and the product's rating that follows them. */
class ReviewFlowIT extends AbstractIntegrationTest {

    @Test
    void review_isPublished_andCountsInTheProductsRating() throws Exception {
        UUID phone = stub.product("PIXEL-8");

        review(customer, phone, 4, "  Great camera  ", "Battery could be better")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Review published"))
                .andExpect(jsonPath("$.data.reviewerName").value("Omesh K."))
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.title").value("Great camera"))
                .andExpect(jsonPath("$.data.verifiedPurchase").value(false))
                .andExpect(jsonPath("$.data.status").value("VISIBLE"))
                .andExpect(jsonPath("$.data.userId").doesNotExist());

        JsonNode summary = summary(phone);
        assertThat(summary.get("averageRating").decimalValue()).isEqualByComparingTo("4.0");
        assertThat(summary.get("reviewCount").asInt()).isEqualTo(1);
        assertThat(summary.get("ratingCounts").get("4").asInt()).isEqualTo(1);
        assertThat(summary.get("ratingCounts").get("5").asInt()).isZero();

        // Anyone can read reviews, without a token
        mockMvc.perform(get(REVIEWS_URL + "/products/" + phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].comment").value("Battery could be better"))
                .andExpect(jsonPath("$.data.content[0].userId").doesNotExist());
    }

    @Test
    void productWithoutReviews_hasAnEmptySummary() throws Exception {
        JsonNode summary = summary(UUID.randomUUID());

        assertThat(summary.get("averageRating").decimalValue()).isEqualByComparingTo("0");
        assertThat(summary.get("reviewCount").asInt()).isZero();
        assertThat(summary.get("ratingCounts")).hasSize(5);
    }

    @Test
    void average_isRoundedToOneDecimal() throws Exception {
        UUID phone = stub.product("PIXEL-8");

        reviewId(customer, phone, 5);
        reviewId(otherCustomer, phone, 4);
        reviewId(newCustomerToken(), phone, 4);

        JsonNode summary = summary(phone);
        assertThat(summary.get("averageRating").decimalValue()).isEqualByComparingTo("4.3"); // 13 / 3 = 4.33
        assertThat(summary.get("reviewCount").asInt()).isEqualTo(3);
        assertThat(summary.get("ratingCounts").get("4").asInt()).isEqualTo(2);
    }

    @Test
    void secondReviewOfTheSameProduct_returns409() throws Exception {
        UUID phone = stub.product("PIXEL-8");
        reviewId(customer, phone, 5);

        review(customer, phone, 1, "Changed my mind", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You have already reviewed this product. Edit your review instead."));
    }

    @Test
    void productNotForSale_returns404_andCatalogDown_returns503() throws Exception {
        review(customer, UUID.randomUUID(), 5, "Nice", null).andExpect(status().isNotFound());

        UUID phone = stub.product("PIXEL-8");
        stub.setDown(Service.PRODUCT, true);
        review(customer, phone, 5, "Nice", null).andExpect(status().isServiceUnavailable());
    }

    @Test
    void invalidReview_returns400() throws Exception {
        UUID phone = stub.product("PIXEL-8");

        review(customer, phone, 6, "", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.rating").value("rating must be between 1 and 5"))
                .andExpect(jsonPath("$.validationErrors.title").value("title is required"));
    }

    @Test
    void userServiceDown_reviewIsStillSaved_asCustomer() throws Exception {
        UUID phone = stub.product("PIXEL-8");
        stub.setDown(Service.USER, true);

        review(customer, phone, 5, "Nice", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reviewerName").value("Customer"));
    }

    @Test
    void edit_changesTheRating_onlyForTheAuthor() throws Exception {
        UUID phone = stub.product("PIXEL-8");
        UUID id = reviewId(customer, phone, 2);

        String body = "{\"rating\":5,\"title\":\"Better after the update\",\"comment\":\"\"}";
        mockMvc.perform(put(REVIEWS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, otherCustomer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(REVIEWS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.comment").doesNotExist())
                .andExpect(jsonPath("$.data.editedAt").exists());

        assertThat(summary(phone).get("averageRating").decimalValue()).isEqualByComparingTo("5.0");
    }

    @Test
    void delete_byAuthorOrAdmin_notByOthers() throws Exception {
        UUID phone = stub.product("PIXEL-8");
        UUID mine = reviewId(customer, phone, 5);
        UUID theirs = reviewId(otherCustomer, phone, 1);

        mockMvc.perform(delete(REVIEWS_URL + "/" + theirs).header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(REVIEWS_URL + "/" + mine).header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isNoContent());
        assertThat(summary(phone).get("averageRating").decimalValue()).isEqualByComparingTo("1.0");

        mockMvc.perform(delete(REVIEWS_URL + "/" + theirs).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        assertThat(summary(phone).get("reviewCount").asInt()).isZero();
        assertThat(summary(phone).get("averageRating").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void productReviews_canBeFilteredAndSorted() throws Exception {
        UUID phone = stub.product("PIXEL-8");
        UUID five = reviewId(customer, phone, 5);
        reviewId(otherCustomer, phone, 3);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post(REVIEWS_URL + "/" + five + "/helpful").header(HttpHeaders.AUTHORIZATION, otherCustomer))
                .andExpect(status().isOk());

        mockMvc.perform(get(REVIEWS_URL + "/products/" + phone).param("rating", "3"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].rating").value(3));
        mockMvc.perform(get(REVIEWS_URL + "/products/" + phone).param("sort", "helpfulCount,desc"))
                .andExpect(jsonPath("$.data.content[0].id").value(five.toString()));
        mockMvc.perform(get(REVIEWS_URL + "/products/" + phone).param("verifiedOnly", "true"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get(REVIEWS_URL + "/products/" + phone).param("sort", "userId"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot sort by 'userId'")));
        mockMvc.perform(get(REVIEWS_URL + "/products/" + phone).param("rating", "9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myReviews_listOnlyMine() throws Exception {
        reviewId(customer, stub.product("A"), 5);
        reviewId(customer, stub.product("B"), 4);
        reviewId(otherCustomer, stub.product("C"), 3);

        mockMvc.perform(get(REVIEWS_URL + "/me").header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void writing_needsALogin_readingDoesNot() throws Exception {
        UUID phone = stub.product("PIXEL-8");

        review("", phone, 5, "Nice", null).andExpect(status().isUnauthorized());
        mockMvc.perform(get(REVIEWS_URL + "/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(REVIEWS_URL + "/products/" + phone)).andExpect(status().isOk());
        mockMvc.perform(get(REVIEWS_URL + "/products/" + phone + "/summary")).andExpect(status().isOk());
    }
}
