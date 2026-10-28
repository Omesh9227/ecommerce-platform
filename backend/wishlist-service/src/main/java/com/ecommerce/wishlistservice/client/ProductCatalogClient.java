package com.ecommerce.wishlistservice.client;

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

/**
 * product-service's public GET /api/v1/products/lookup?ids=... - one request for the whole wishlist.
 * It returns only products that are for sale (ACTIVE); a missing id means "no longer sold".
 */
@Slf4j
@Component
public class ProductCatalogClient {

    static final String LOOKUP_PATH = "/api/v1/products/lookup";
    static final String WHAT = "The product catalog";

    private final RestClient restClient;

    public ProductCatalogClient(@Qualifier("productServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** @throws DownstreamUnavailableException if product-service can't answer */
    public Map<UUID, CatalogProduct> findActiveProducts(Collection<UUID> ids) {
        Set<UUID> distinctIds = new LinkedHashSet<>(ids);
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        String idList = distinctIds.stream().map(UUID::toString).collect(Collectors.joining(","));

        ApiEnvelope<List<CatalogProduct>> response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path(LOOKUP_PATH).queryParam("ids", idList).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiEnvelope<List<CatalogProduct>>>() {
                    });
        } catch (RestClientException ex) {
            // Any failure here (down, timeout, 5xx, or a 4xx meaning our request is wrong) isn't the customer's fault
            log.warn("product-service lookup failed for {} product(s): {}", distinctIds.size(), ex.getMessage());
            throw new DownstreamUnavailableException(WHAT, ex);
        }
        if (response == null || response.data() == null) {
            throw new DownstreamUnavailableException(WHAT, null);
        }
        return response.data().stream()
                .filter(p -> p.id() != null && distinctIds.contains(p.id()))
                .collect(Collectors.toMap(CatalogProduct::id, Function.identity(), (first, second) -> first));
    }
}
