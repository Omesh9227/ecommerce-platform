package com.ecommerce.orderservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** lb:// URLs go through the load-balanced client (Eureka), fixed URLs through the plain one. No network used. */
class ServiceUrlsTest {

    private final List<URI> plainCalls = new ArrayList<>();
    private final List<URI> loadBalancedCalls = new ArrayList<>();
    private final RestClient.Builder plain = RestClient.builder().requestInterceptor(recordInto(plainCalls));
    private final RestClient.Builder loadBalanced = RestClient.builder().requestInterceptor(recordInto(loadBalancedCalls));

    /** Records where the request would go and answers 200 itself, so nothing is sent. */
    private static ClientHttpRequestInterceptor recordInto(List<URI> calls) {
        return (request, body, execution) -> {
            calls.add(request.getURI());
            return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        };
    }

    @Test
    void lbUrl_usesTheLoadBalancedClient_withTheServiceNameAsHost() {
        RestClient client = ServiceUrls.builderFor("lb://product-service", plain, loadBalanced).build();

        client.get().uri("/api/v1/products/lookup?ids={ids}", "42").retrieve().toBodilessEntity();

        assertThat(loadBalancedCalls).containsExactly(URI.create("http://product-service/api/v1/products/lookup?ids=42"));
        assertThat(plainCalls).isEmpty();
    }

    @Test
    void fixedUrl_usesThePlainClient_asIs() {
        RestClient client = ServiceUrls.builderFor("http://localhost:8082", plain, loadBalanced).build();

        client.get().uri("/api/v1/products/lookup").retrieve().toBodilessEntity();

        assertThat(plainCalls).containsExactly(URI.create("http://localhost:8082/api/v1/products/lookup"));
        assertThat(loadBalancedCalls).isEmpty();
    }

    @Test
    void lbUrlWithoutServiceName_isRejectedAtStartup() {
        assertThatThrownBy(() -> ServiceUrls.builderFor("lb://", plain, loadBalanced))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lb://<service-name>");
    }

    @Test
    void sharedBuilder_isNotChangedByAClient() {
        ServiceUrls.builderFor("lb://product-service", plain, loadBalanced).build();

        // A client built later from the shared builder doesn't inherit http://product-service as its base URL
        loadBalanced.build().get().uri("http://other-service/x").retrieve().toBodilessEntity();

        assertThat(loadBalancedCalls).containsExactly(URI.create("http://other-service/x"));
    }
}
