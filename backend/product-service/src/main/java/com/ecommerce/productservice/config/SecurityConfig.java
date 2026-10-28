package com.ecommerce.productservice.config;

import com.ecommerce.productservice.security.JwtAuthenticationFilter;
import com.ecommerce.productservice.security.JwtValidator;
import com.ecommerce.productservice.security.RestAccessDeniedHandler;
import com.ecommerce.productservice.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Browsing the catalog is public; changing it needs ADMIN.
 * CORS is not configured here: browsers talk to the api-gateway, which handles CORS for all services.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] CATALOG_PATHS = {"/api/v1/products/**", "/api/v1/categories/**"};

    private static final String[] PUBLIC_DOC_ENDPOINTS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private static final String[] PUBLIC_ACTUATOR_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };

    private final JwtValidator jwtValidator;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, CATALOG_PATHS).permitAll()
                        .requestMatchers(PUBLIC_DOC_ENDPOINTS).permitAll()
                        .requestMatchers(PUBLIC_ACTUATOR_ENDPOINTS).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(CATALOG_PATHS).hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtValidator), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
