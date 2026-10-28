package com.ecommerce.cartservice.config;

import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for product-service. Short timeouts on purpose: if product-service is slow, the cart answers
 * quickly with what it has (see CartServiceImpl) instead of making the user wait.
 *
 * services.product-service.url is lb://product-service (found through Eureka) or a fixed http://host:port.
 */
@Configuration
public class HttpClientConfig {

    /** Spring Boot's usual RestClient builder, for fixed URLs. @Primary: the one injected by default. */
    @Bean
    @Primary
    public RestClient.Builder plainRestClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }

    /** The same builder plus Spring Cloud LoadBalancer: http://product-service goes to an instance listed in Eureka. */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }

    @Bean
    public RestClient productServiceRestClient(RestClient.Builder plainRestClientBuilder,
                                               @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder,
                                               ProductServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return ServiceUrls.builderFor(properties.url(), plainRestClientBuilder, loadBalancedRestClientBuilder)
                .requestFactory(requestFactory)
                .build();
    }
}
