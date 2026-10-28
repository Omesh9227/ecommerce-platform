package com.ecommerce.userservice.security;

import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
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
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads "Authorization: Bearer <token>", validates it and populates the SecurityContext.
 *
 * It never rejects a request itself: on a bad token it records the reason as a request attribute and
 * lets the chain continue unauthenticated. Public endpoints still work; protected ones are rejected by
 * Spring Security, and RestAuthenticationEntryPoint returns the recorded reason as a 401.
 *
 * The user is re-loaded from the database on each request so that a suspended account is locked out
 * immediately rather than when its token expires.
 *
 * Not a @Component on purpose: Spring Boot would otherwise also register it as a plain servlet filter.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "com.ecommerce.userservice.authError";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = jwtService.parseAndValidate(token);
            String subject = claims.getSubject();
            if (subject == null) {
                throw new MalformedJwtException("Token has no subject");
            }
            authenticate(UUID.fromString(subject), request);
        } catch (ExpiredJwtException ex) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Access token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            // Never log the token itself
            log.debug("Rejected JWT on {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Invalid access token");
        }

        chain.doFilter(request, response);
    }

    private void authenticate(UUID userId, HttpServletRequest request) {
        Optional<User> found = userRepository.findById(userId);
        if (found.isEmpty()) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Invalid access token");
            return;
        }
        User user = found.get();
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Account is not active");
            return;
        }

        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(user.getRole().authority())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
