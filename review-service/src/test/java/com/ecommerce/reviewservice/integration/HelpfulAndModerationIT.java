package com.ecommerce.reviewservice.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Helpful" votes, and admins hiding reviews. */
class HelpfulAndModerationIT extends AbstractIntegrationTest {

    private ResultActions vote(String auth, UUID reviewId) throws Exception {
        return mockMvc.perform(post(REVIEWS_URL + "/" + reviewId + "/helpful").header(HttpHeaders.AUTHORIZATION, auth));
    }

    private ResultActions hide(String auth, UUID reviewId, String reason) throws Exception {
        return mockMvc.perform(post(ADMIN_URL + "/" + reviewId + "/hide").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"));
    }

    @Test
    void helpfulVotes_oncePerCustomer_notOnYourOwnReview() throws Exception {
        UUID id = reviewId(customer, stub.product("PIXEL-8"), 5);

        vote(otherCustomer, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.helpfulCount").value(1))
                .andExpect(jsonPath("$.data.votedByMe").value(true));
        vote(otherCustomer, id).andExpect(jsonPath("$.data.helpfulCount").value(1));
        vote(newCustomerToken(), id).andExpect(jsonPath("$.data.helpfulCount").value(2));
        vote(customer, id)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can't mark your own review as helpful"));

        mockMvc.perform(delete(REVIEWS_URL + "/" + id + "/helpful").header(HttpHeaders.AUTHORIZATION, otherCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.helpfulCount").value(1))
                .andExpect(jsonPath("$.data.votedByMe").value(false));
        mockMvc.perform(delete(REVIEWS_URL + "/" + id + "/helpful").header(HttpHeaders.AUTHORIZATION, otherCustomer))
                .andExpect(jsonPath("$.data.helpfulCount").value(1));
    }

    @Test
    void hiddenReview_isNotShownOrCounted_andItsAuthorSeesWhy() throws Exception {
        UUID phone = stub.product("PIXEL-8");
        UUID spam = reviewId(customer, phone, 1);
        reviewId(otherCustomer, phone, 5);

        hide(admin, spam, "Contains a phone number")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"))
                .andExpect(jsonPath("$.data.userId").value(customerId.toString()));

        mockMvc.perform(get(REVIEWS_URL + "/products/" + phone)).andExpect(jsonPath("$.data.totalElements").value(1));
        assertThat(summary(phone).get("averageRating").decimalValue()).isEqualByComparingTo("5.0");
        assertThat(summary(phone).get("reviewCount").asInt()).isEqualTo(1);
        mockMvc.perform(get(REVIEWS_URL + "/me").header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(jsonPath("$.data.content[0].status").value("HIDDEN"))
                .andExpect(jsonPath("$.data.content[0].hiddenReason").value("Contains a phone number"));
        vote(otherCustomer, spam).andExpect(status().isNotFound());

        mockMvc.perform(post(ADMIN_URL + "/" + spam + "/unhide").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VISIBLE"));
        assertThat(summary(phone).get("averageRating").decimalValue()).isEqualByComparingTo("3.0");
    }

    @Test
    void adminList_canFilterByStatus() throws Exception {
        UUID phone = stub.product("PIXEL-8");
        UUID hidden = reviewId(customer, phone, 1);
        reviewId(otherCustomer, phone, 5);
        hide(admin, hidden, "Spam").andExpect(status().isOk());

        mockMvc.perform(get(ADMIN_URL).param("status", "HIDDEN").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(hidden.toString()));
        mockMvc.perform(get(ADMIN_URL).param("productId", phone.toString()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void moderation_isForAdminsOnly() throws Exception {
        UUID id = reviewId(customer, stub.product("PIXEL-8"), 1);

        hide(customer, id, "I don't like it").andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_URL).header(HttpHeaders.AUTHORIZATION, customer)).andExpect(status().isForbidden());
        hide(admin, id, "").andExpect(status().isBadRequest());
        hide(admin, UUID.randomUUID(), "Spam").andExpect(status().isNotFound());
    }
}
