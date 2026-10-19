package com.ecommerce.apigateway.filter;

import com.ecommerce.apigateway.config.GatewaySecurityProperties;
import com.ecommerce.apigateway.exception.ErrorResponseWriter;
import com.ecommerce.apigateway.security.JwtValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

/**
 * Runs for every routed request, before it is forwarded:
 *
 * 1. Rejects path tricks ("..", "//", encoded dots) that could bypass the path rules.
 * 2. Public paths (register, login) pass through without a token.
 * 3. Everything else needs a valid Bearer token → otherwise 401.
 * 4. Admin paths also need role ADMIN → otherwise 403.
 * 5. Forwards the request with trusted identity headers (X-User-Id, X-User-Email, X-User-Role).
 *    Client-supplied copies of these headers are always removed first, so they can't be spoofed.
 *
 * The original Authorization header is still forwarded: services keep validating the token themselves
 * (defence in depth) and do checks the gateway can't, e.g. user-service rejecting suspended accounts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    private static final List<String> IDENTITY_HEADERS = List.of(USER_ID_HEADER, USER_EMAIL_HEADER, USER_ROLE_HEADER);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ADMIN_ROLE = "ADMIN";

    private static final String MSG_AUTH_REQUIRED = "Authentication is required to access this resource";
    private static final String MSG_INVALID_TOKEN = "Invalid access token";
    private static final String MSG_EXPIRED_TOKEN = "Access token has expired";
    private static final String MSG_FORBIDDEN = "You do not have permission to access this resource";

    private final JwtValidator jwtValidator;
    private final GatewaySecurityProperties securityProperties;
    private final ErrorResponseWriter errorResponseWriter;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (isSuspiciousPath(path)) {
            return errorResponseWriter.write(exchange, HttpStatus.BAD_REQUEST, "Invalid request path");
        }

        if (matchesAny(securityProperties.publicPaths(), path)) {
            return chain.filter(withIdentity(exchange, null, null, null));
        }

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, MSG_AUTH_REQUIRED);
        }

        Claims claims;
        try {
            claims = jwtValidator.validate(header.substring(BEARER_PREFIX.length()).trim());
        } catch (ExpiredJwtException ex) {
            return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, MSG_EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException ex) {
            // Never log the token itself
            log.debug("Rejected JWT on {}: {}", path, ex.getClass().getSimpleName());
            return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, MSG_INVALID_TOKEN);
        }

        String userId = claims.getSubject();
        String role = claims.get(JwtValidator.CLAIM_ROLE, String.class);
        String email = claims.get(JwtValidator.CLAIM_EMAIL, String.class);
        if (userId == null || role == null) {
            return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, MSG_INVALID_TOKEN);
        }

        if (matchesAny(securityProperties.adminPaths(), path) && !ADMIN_ROLE.equals(role)) {
            log.debug("User {} with role {} denied access to {}", userId, role, path);
            return errorResponseWriter.write(exchange, HttpStatus.FORBIDDEN, MSG_FORBIDDEN);
        }

        return chain.filter(withIdentity(exchange, userId, email, role));
    }

    /** Strips any client-supplied identity headers, then adds the verified ones (if any). */
    private ServerWebExchange withIdentity(ServerWebExchange exchange, String userId, String email, String role) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    IDENTITY_HEADERS.forEach(headers::remove);
                    if (userId != null) {
                        headers.set(USER_ID_HEADER, userId);
                        headers.set(USER_ROLE_HEADER, role);
                        if (email != null) {
                            headers.set(USER_EMAIL_HEADER, email);
                        }
                    }
                })
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private boolean matchesAny(List<String> patterns, String path) {
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSuspiciousPath(String rawPath) {
        String lower = rawPath.toLowerCase(Locale.ROOT);
        return lower.contains("/../") || lower.endsWith("/..")
                || lower.contains("/./") || lower.endsWith("/.")
                || lower.contains("//")
                || lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c")
                || lower.contains("\\") || lower.contains(";");
    }

    /** Before routing filters, after request logging (which is a WebFilter and always runs first). */
    @Override
    public int getOrder() {
        return -100;
    }
}
