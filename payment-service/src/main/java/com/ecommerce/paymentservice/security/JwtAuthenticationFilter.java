package com.ecommerce.paymentservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Reads "Authorization: Bearer <token>", validates it and populates the SecurityContext.
 *
 * Like user-service, it never rejects a request itself: a bad token just leaves the request anonymous
 * (public GET endpoints still work) and the reason is returned by RestAuthenticationEntryPoint if the
 * endpoint needs authentication.
 *
 * Unlike user-service there is no database lookup: this service has no user table, so the role comes
 * from the verified token. (A suspended admin's token keeps working here until it expires - at most
 * JWT_EXPIRATION_SECONDS. A shared token-revocation check can be added later.)
 *
 * Not a @Component on purpose: Spring Boot would otherwise also register it as a plain servlet filter.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "com.ecommerce.paymentservice.authError";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtValidator.validate(header.substring(BEARER_PREFIX.length()).trim());
            String subject = claims.getSubject();
            String role = claims.get(JwtValidator.CLAIM_ROLE, String.class);
            if (subject == null || role == null) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Invalid access token");
            } else {
                authenticate(new AuthenticatedUser(UUID.fromString(subject),
                        claims.get(JwtValidator.CLAIM_EMAIL, String.class), role), request);
            }
        } catch (ExpiredJwtException ex) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Access token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT on {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Invalid access token");
        }

        chain.doFilter(request, response);
    }

    private static void authenticate(AuthenticatedUser principal, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
        authentication.setDetails(request.getRemoteAddr());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
