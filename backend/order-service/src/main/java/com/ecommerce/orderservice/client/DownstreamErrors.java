package com.ecommerce.orderservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Turns RestClient failures into the two cases callers care about: "refused" (4xx) or "unavailable". */
@Slf4j
final class DownstreamErrors {

    private static final ObjectMapper JSON = new ObjectMapper();

    private DownstreamErrors() {
    }

    static RuntimeException translate(String service, RestClientException ex) {
        if (ex instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status == 401 || status == 403) {
                // Our own credentials were refused: a configuration problem (e.g. JWT_SECRET differs), not the user's
                log.error("{} refused order-service's credentials ({}). Check JWT_SECRET / roles.", service, status);
                return new DownstreamUnavailableException(service, ex);
            }
            if (status >= 400 && status < 500) {
                return new DownstreamRejectedException(service, status, messageOf(response));
            }
        }
        log.warn("{} call failed: {}", service, ex.getMessage());
        return new DownstreamUnavailableException(service, ex);
    }

    /** The "message" field of the other service's ErrorResponse, if there is one. */
    private static String messageOf(RestClientResponseException response) {
        try {
            JsonNode body = JSON.readTree(response.getResponseBodyAsString());
            if (body != null && body.hasNonNull("message")) {
                return body.get("message").asText();
            }
        } catch (Exception ignored) {
            // not JSON - fall through
        }
        return response.getStatusText();
    }
}
