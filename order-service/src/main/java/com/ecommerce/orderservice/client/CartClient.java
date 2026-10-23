package com.ecommerce.orderservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * cart-service, called with the customer's own token (forwarded), so cart-service returns that customer's cart.
 */
@Slf4j
@Component
public class CartClient {

    static final String SERVICE = "cart-service";
    static final String CART_PATH = "/api/v1/cart";

    /** The parts of cart-service's CartResponse that checkout needs. */
    public record CartView(List<Line> items, BigDecimal subtotal, boolean checkoutReady, boolean pricesUpToDate,
                           List<String> warnings) {

        public record Line(UUID productId, String name, int quantity, String status, String message) {
        }
    }

    private final RestClient restClient;

    public CartClient(@Qualifier("cartServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public CartView getCart(String bearerToken) {
        try {
            ApiEnvelope<CartView> response = restClient.get()
                    .uri(CART_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiEnvelope<CartView>>() {
                    });
            if (response == null || response.data() == null) {
                throw new DownstreamUnavailableException(SERVICE, null);
            }
            return response.data();
        } catch (RestClientException ex) {
            throw DownstreamErrors.translate(SERVICE, ex);
        }
    }

    /** Best effort: the order is already placed, so a failure here is only logged. */
    public void clearCart(String bearerToken) {
        try {
            restClient.delete()
                    .uri(CART_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("Order placed but the cart could not be cleared: {}", ex.getMessage());
        }
    }
}
