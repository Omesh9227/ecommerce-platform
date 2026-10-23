package com.ecommerce.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** One HTTP client per downstream service, each with short timeouts so a slow service fails fast. */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient cartServiceRestClient(RestClient.Builder builder, DownstreamServicesProperties services) {
        return client(builder, services.cartService().url(), services);
    }

    @Bean
    public RestClient productServiceRestClient(RestClient.Builder builder, DownstreamServicesProperties services) {
        return client(builder, services.productService().url(), services);
    }

    @Bean
    public RestClient inventoryServiceRestClient(RestClient.Builder builder, DownstreamServicesProperties services) {
        return client(builder, services.inventoryService().url(), services);
    }

    private static RestClient client(RestClient.Builder builder, String baseUrl, DownstreamServicesProperties services) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(services.connectTimeout());
        requestFactory.setReadTimeout(services.readTimeout());
        // clone(): the auto-configured builder is shared, so each client must not change it for the others
        return builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
