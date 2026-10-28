package com.ecommerce.reviewservice.config;

import org.springframework.web.client.RestClient;

/**
 * Picks the HTTP client for a configured service URL:
 * <ul>
 *   <li>{@code lb://product-service} - load-balanced client, base URL {@code http://product-service}: Spring Cloud
 *       LoadBalancer replaces the name with a running instance registered in Eureka (discovery-server).</li>
 *   <li>{@code http://localhost:8082} - plain client, URL used as is (tests point it at a stub server).</li>
 * </ul>
 * The shared builders are cloned, so one client's base URL or timeouts never leak into another.
 */
final class ServiceUrls {

    static final String LOAD_BALANCED_PREFIX = "lb://";

    private ServiceUrls() {
    }

    static RestClient.Builder builderFor(String url, RestClient.Builder plain, RestClient.Builder loadBalanced) {
        if (url.startsWith(LOAD_BALANCED_PREFIX)) {
            String serviceName = url.substring(LOAD_BALANCED_PREFIX.length());
            if (serviceName.isBlank()) {
                throw new IllegalArgumentException("No service name in '" + url + "' (expected lb://<service-name>)");
            }
            return loadBalanced.clone().baseUrl("http://" + serviceName);
        }
        return plain.clone().baseUrl(url);
    }
}
