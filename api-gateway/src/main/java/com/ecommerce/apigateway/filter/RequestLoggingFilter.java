package com.ecommerce.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Runs first for every request (including 401s, 404s and CORS preflights):
 * - assigns a correlation id (or keeps a valid one sent by the client),
 * - forwards it to the downstream service as X-Correlation-Id,
 * - returns it in the response, so a client error report can be matched to the logs,
 * - logs one line per request: method, path, status, duration.
 *
 * Query strings and headers are never logged (they can carry tokens or personal data).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter implements WebFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startNanos = System.nanoTime();

        String incoming = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        String correlationId = incoming != null && VALID_CORRELATION_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(CORRELATION_ID_HEADER, correlationId))
                .build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();

        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
            logRequest(request, mutated.getResponse().getStatusCode(), startNanos, correlationId);
            return Mono.empty();
        });

        return chain.filter(mutated);
    }

    private static void logRequest(ServerHttpRequest request, HttpStatusCode status, long startNanos, String correlationId) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        String path = request.getPath().value();
        int statusValue = status != null ? status.value() : 0;

        if (path.startsWith("/actuator")) {
            log.debug("{} {} -> {} ({} ms) [correlationId={}]", request.getMethod(), path, statusValue, durationMs, correlationId);
        } else {
            log.info("{} {} -> {} ({} ms) [correlationId={}]", request.getMethod(), path, statusValue, durationMs, correlationId);
        }
    }
}
