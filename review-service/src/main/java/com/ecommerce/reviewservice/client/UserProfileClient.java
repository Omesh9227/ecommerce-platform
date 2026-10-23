package com.ecommerce.reviewservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Locale;

/**
 * user-service's GET /api/v1/users/me, called with the reviewer's own token, for the name shown on the review:
 * "Omesh K." (first name + initial). If user-service can't answer, the review is still saved, as "Customer" -
 * never with the email address.
 */
@Slf4j
@Component
public class UserProfileClient {

    static final String ME_PATH = "/api/v1/users/me";
    public static final String FALLBACK_NAME = "Customer";

    record Profile(String firstName, String lastName) {
    }

    private final RestClient restClient;

    public UserProfileClient(@Qualifier("userServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public String displayName(String bearerToken) {
        try {
            ApiEnvelope<Profile> response = restClient.get()
                    .uri(ME_PATH)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiEnvelope<Profile>>() {
                    });
            return response == null || response.data() == null ? FALLBACK_NAME
                    : displayName(response.data().firstName(), response.data().lastName());
        } catch (RestClientException ex) {
            log.warn("user-service profile lookup failed, reviewer shown as '{}': {}", FALLBACK_NAME, ex.getMessage());
            return FALLBACK_NAME;
        }
    }

    /** "omesh", "kumar" -> "Omesh K."; no last name -> "Omesh"; nothing usable -> "Customer". */
    static String displayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.strip();
        String last = lastName == null ? "" : lastName.strip();
        if (first.isEmpty()) {
            return FALLBACK_NAME;
        }
        String name = first.substring(0, 1).toUpperCase(Locale.ROOT) + first.substring(1);
        if (!last.isEmpty()) {
            name += " " + last.substring(0, 1).toUpperCase(Locale.ROOT) + ".";
        }
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
