package com.ecommerce.orderservice.config;

import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * One HTTP client per downstream service, each with short timeouts so a slow service fails fast.
 * Each services.*.url is lb://&lt;service-name&gt; (found through Eureka) or a fixed http://host:port.
 */
@Configuration
public class HttpClientConfig {

    /** Spring Boot's usual RestClient builder, for fixed URLs. @Primary: the one injected by default. */
    @Bean
    @Primary
    public RestClient.Builder plainRestClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }

    /** The same builder plus Spring Cloud LoadBalancer: http://cart-service goes to an instance listed in Eureka. */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }

    @Bean
    public RestClient cartServiceRestClient(RestClient.Builder plainRestClientBuilder,
                                            @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder,
                                            DownstreamServicesProperties services) {
        return client(services.cartService().url(), plainRestClientBuilder, loadBalancedRestClientBuilder, services);
    }

    @Bean
    public RestClient productServiceRestClient(RestClient.Builder plainRestClientBuilder,
                                               @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder,
                                               DownstreamServicesProperties services) {
        return client(services.productService().url(), plainRestClientBuilder, loadBalancedRestClientBuilder, services);
    }

    @Bean
    public RestClient inventoryServiceRestClient(RestClient.Builder plainRestClientBuilder,
                                                 @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder,
                                                 DownstreamServicesProperties services) {
        return client(services.inventoryService().url(), plainRestClientBuilder, loadBalancedRestClientBuilder, services);
    }

    private static RestClient client(String url, RestClient.Builder plain, RestClient.Builder loadBalanced,
                                     DownstreamServicesProperties services) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(services.connectTimeout());
        requestFactory.setReadTimeout(services.readTimeout());
        return ServiceUrls.builderFor(url, plain, loadBalanced).requestFactory(requestFactory).build();
    }
}
