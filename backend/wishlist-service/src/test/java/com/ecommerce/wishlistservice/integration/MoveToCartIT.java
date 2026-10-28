package com.ecommerce.wishlistservice.integration;

import com.ecommerce.wishlistservice.support.StubServices.CartRequest;
import com.ecommerce.wishlistservice.support.StubServices.Service;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Move to cart": through cart-service with the customer's own token; removed from the wishlist only if it worked. */
class MoveToCartIT extends AbstractIntegrationTest {

    /** One token for the whole test, so the forwarded header can be compared exactly. */
    private final String auth = customerAuth();

    private ResultActions moveToCart(UUID productId, String body) throws Exception {
        var request = post(ITEMS_URL + "/" + productId + "/move-to-cart").header(HttpHeaders.AUTHORIZATION, auth);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    @Test
    void moved_withTheCustomersToken_andRemovedFromTheWishlist() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 5);
        UUID case_ = stub.product("CASE", "500", "400", 5);
        add(auth, phone);
        add(auth, case_);

        moveToCart(phone, "{\"quantity\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Moved to your cart"))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(case_.toString()));

        assertThat(stub.cartRequests()).containsExactly(new CartRequest(auth, phone, 2));
    }

    @Test
    void withoutBody_movesOne() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 5);
        add(auth, phone);

        moveToCart(phone, null).andExpect(status().isOk());

        assertThat(stub.cartRequests()).extracting(CartRequest::quantity).containsExactly(1);
        assertThat(wishlist(auth).get("totalItems").asInt()).isZero();
    }

    @Test
    void cartRefuses_itsAnswerIsPassedOn_andTheProductStaysSaved() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 0);
        add(auth, phone);
        stub.cartRefuses(409, "Product PIXEL-8 is out of stock");

        moveToCart(phone, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product PIXEL-8 is out of stock"));

        assertThat(wishlist(auth).get("totalItems").asInt()).isEqualTo(1);
    }

    @Test
    void cartServiceDown_returns503_andTheProductStaysSaved() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 5);
        add(auth, phone);
        stub.setDown(Service.CART, true);

        moveToCart(phone, null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The cart is temporarily unavailable. Please try again shortly."));

        assertThat(wishlist(auth).get("totalItems").asInt()).isEqualTo(1);
    }

    @Test
    void productNotInWishlist_returns404_withoutCallingTheCart() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 5);

        moveToCart(phone, null).andExpect(status().isNotFound());

        assertThat(stub.cartRequests()).isEmpty();
    }

    @Test
    void invalidQuantity_returns400() throws Exception {
        UUID phone = stub.product("PIXEL-8", "2000", "1800", 5);
        add(auth, phone);

        moveToCart(phone, "{\"quantity\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.quantity").value("quantity must be at least 1"));

        assertThat(stub.cartRequests()).isEmpty();
    }
}
