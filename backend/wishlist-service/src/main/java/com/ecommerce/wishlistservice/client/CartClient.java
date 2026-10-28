package com.ecommerce.wishlistservice.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

/**
 * cart-service, called with the customer's own token (forwarded), so the product lands in that customer's cart and
 * cart-service applies all its usual rules (stock, quantity limits).
 */
@Component
public class CartClient {

    static final String ITEMS_PATH = "/api/v1/cart/items";
    static final String WHAT = "The cart";

    private final RestClient restClient;

    public CartClient(@Qualifier("cartServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * @throws DownstreamRejectedException    cart-service said no (e.g. 409 out of stock, 400 cart full) - with its message
     * @throws DownstreamUnavailableException cart-service is down or failed
     */
    public void addItem(String bearerToken, UUID productId, int quantity) {
        try {
            restClient.post()
                    .uri(ITEMS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("productId", productId.toString(), "quantity", quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw DownstreamErrors.translate(WHAT, ex);
        }
    }
}
