package com.ecommerce.cartservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for product-service. Short timeouts on purpose: if product-service is slow, the cart answers
 * quickly with what it has (see CartServiceImpl) instead of making the user wait.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient productServiceRestClient(RestClient.Builder builder, ProductServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder
                .baseUrl(properties.url())
                .requestFactory(requestFactory)
                .build();
    }
}
