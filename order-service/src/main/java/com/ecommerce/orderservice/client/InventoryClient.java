package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.security.ServiceTokenProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

/**
 * inventory-service's reservation API, called with order-service's own service token (role SERVICE).
 * Every call is idempotent on inventory's side, so retrying is always safe.
 */
@Component
public class InventoryClient {

    static final String SERVICE = "inventory-service";
    static final String RESERVATIONS_PATH = "/api/v1/inventory/reservations";

    public record ReservationLine(UUID productId, int quantity) {
    }

    public record ReservationView(String orderReference, String status, List<ReservationLine> items) {

        public boolean isReserved() {
            return "RESERVED".equals(status);
        }
    }

    public record ReserveRequest(String orderReference, List<ReservationLine> items) {
    }

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;

    public InventoryClient(@Qualifier("inventoryServiceRestClient") RestClient restClient,
                           ServiceTokenProvider serviceTokenProvider) {
        this.restClient = restClient;
        this.serviceTokenProvider = serviceTokenProvider;
    }

    /** Holds stock for an order, all or nothing. Repeating it returns the existing reservation. */
    public ReservationView reserve(String orderReference, List<ReservationLine> items) {
        try {
            return unwrap(restClient.post()
                    .uri(RESERVATIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, serviceAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReserveRequest(orderReference, items))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiEnvelope<ReservationView>>() {
                    }));
        } catch (RestClientException ex) {
            throw DownstreamErrors.translate(SERVICE, ex);
        }
    }

    /** Gives the held stock back (order cancelled or checkout failed). */
    public ReservationView release(String orderReference) {
        return post(RESERVATIONS_PATH + "/{reference}/release", orderReference);
    }

    /** Turns the hold into a sale (order paid). */
    public ReservationView confirm(String orderReference) {
        return post(RESERVATIONS_PATH + "/{reference}/confirm", orderReference);
    }

    /** Undoes a sale (paid order cancelled before shipping): the stock goes back on hand. */
    public ReservationView returnStock(String orderReference) {
        return post(RESERVATIONS_PATH + "/{reference}/return", orderReference);
    }

    private ReservationView post(String path, String orderReference) {
        try {
            return unwrap(restClient.post()
                    .uri(path, orderReference)
                    .header(HttpHeaders.AUTHORIZATION, serviceAuth())
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiEnvelope<ReservationView>>() {
                    }));
        } catch (RestClientException ex) {
            throw DownstreamErrors.translate(SERVICE, ex);
        }
    }

    private String serviceAuth() {
        return "Bearer " + serviceTokenProvider.token();
    }

    private static ReservationView unwrap(ApiEnvelope<ReservationView> response) {
        if (response == null || response.data() == null) {
            throw new DownstreamUnavailableException(SERVICE, null);
        }
        return response.data();
    }
}
