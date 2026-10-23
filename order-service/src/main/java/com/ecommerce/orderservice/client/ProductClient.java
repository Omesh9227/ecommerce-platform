package com.ecommerce.orderservice.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** product-service's public bulk lookup: the source of truth for prices at checkout. */
@Component
public class ProductClient {

    static final String SERVICE = "product-service";
    static final String LOOKUP_PATH = "/api/v1/products/lookup";

    public record ProductView(UUID id, String sku, String name, BigDecimal price, BigDecimal sellingPrice,
                              String currency, List<String> imageUrls) {

        public String firstImageUrl() {
            return imageUrls == null || imageUrls.isEmpty() ? null : imageUrls.get(0);
        }
    }

    private final RestClient restClient;

    public ProductClient(@Qualifier("productServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** ACTIVE products among {@code ids}; a missing id means the product is no longer for sale. */
    public Map<UUID, ProductView> findActiveProducts(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String idList = ids.stream().distinct().map(UUID::toString).collect(Collectors.joining(","));
        try {
            ApiEnvelope<List<ProductView>> response = restClient.get()
                    .uri(uri -> uri.path(LOOKUP_PATH).queryParam("ids", idList).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiEnvelope<List<ProductView>>>() {
                    });
            if (response == null || response.data() == null) {
                throw new DownstreamUnavailableException(SERVICE, null);
            }
            return response.data().stream()
                    .collect(Collectors.toMap(ProductView::id, Function.identity(), (first, second) -> first));
        } catch (RestClientException ex) {
            throw DownstreamErrors.translate(SERVICE, ex);
        }
    }
}
