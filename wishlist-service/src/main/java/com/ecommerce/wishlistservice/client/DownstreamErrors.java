package com.ecommerce.wishlistservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Turns RestClient failures into the two cases callers care about: "refused" (4xx) or "unavailable" (503). */
@Slf4j
final class DownstreamErrors {

    private static final ObjectMapper JSON = new ObjectMapper();

    private DownstreamErrors() {
    }

    static RuntimeException translate(String what, RestClientException ex) {
        if (ex instanceof RestClientResponseException response) {
            int code = response.getStatusCode().value();
            if (code == 401 || code == 403) {
                // The customer's own (already validated) token was refused: a configuration problem, not theirs
                log.error("{} refused the forwarded token ({}). Check JWT_SECRET is the same everywhere.", what, code);
                return new DownstreamUnavailableException(what, ex);
            }
            HttpStatus status = HttpStatus.resolve(code);
            if (status != null && status.is4xxClientError()) {
                return new DownstreamRejectedException(status, messageOf(response));
            }
        }
        log.warn("{} call failed: {}", what, ex.getMessage());
        return new DownstreamUnavailableException(what, ex);
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
