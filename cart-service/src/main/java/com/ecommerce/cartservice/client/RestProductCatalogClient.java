package com.ecommerce.cartservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Calls product-service's public GET /api/v1/products/lookup?ids=... - one request for the whole cart. */
@Slf4j
@Component
public class RestProductCatalogClient implements ProductCatalogClient {

    static final String LOOKUP_PATH = "/api/v1/products/lookup";

    /** product-service's response envelope: { success, message, data: [ ...products ] } */
    public record LookupResponse(boolean success, String message, List<CatalogProduct> data) {
    }

    private final RestClient restClient;

    public RestProductCatalogClient(@Qualifier("productServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Map<UUID, CatalogProduct> findActiveProducts(Collection<UUID> ids) {
        Set<UUID> distinctIds = new LinkedHashSet<>(ids);
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        String idList = distinctIds.stream().map(UUID::toString).collect(Collectors.joining(","));

        LookupResponse response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path(LOOKUP_PATH).queryParam("ids", idList).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<LookupResponse>() {
                    });
        } catch (RestClientException ex) {
            // Connection refused, timeout, 5xx, or a 4xx that means our request is wrong - none can be fixed by the user
            log.warn("product-service lookup failed for {} product(s): {}", distinctIds.size(), ex.getMessage());
            throw new CatalogUnavailableException("The product catalog is temporarily unavailable. Please try again shortly.", ex);
        }

        if (response == null || response.data() == null) {
            throw new CatalogUnavailableException("The product catalog returned an empty response.", null);
        }
        return response.data().stream()
                .filter(p -> p.id() != null && distinctIds.contains(p.id()))
                .collect(Collectors.toMap(CatalogProduct::id, Function.identity(), (first, second) -> first));
    }
}
