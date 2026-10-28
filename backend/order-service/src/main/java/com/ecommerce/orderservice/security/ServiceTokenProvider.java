package com.ecommerce.orderservice.security;

import com.ecommerce.orderservice.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * order-service's own identity when it calls inventory-service: a short-lived JWT with role SERVICE, signed with
 * the platform's shared key. inventory-service accepts role SERVICE only on its reservation endpoints.
 *
 * (With a shared HS256 key every service could sign tokens. A later phase can move to an identity provider with
 * per-service credentials, e.g. OAuth2 client credentials; callers of this class won't change.)
 */
@Component
public class ServiceTokenProvider {

    public static final UUID SERVICE_ID = UUID.nameUUIDFromBytes("order-service".getBytes(StandardCharsets.UTF_8));
    public static final String SERVICE_ROLE = "SERVICE";
    static final String SERVICE_EMAIL = "order-service@internal.ecommerce";
    private static final Duration LIFETIME = Duration.ofMinutes(5);
    private static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(1);

    private record CachedToken(String value, Instant refreshAt) {
    }

    private final SecretKey signingKey;
    private final String issuer;
    private final Clock clock;
    private volatile CachedToken cached;

    public ServiceTokenProvider(JwtProperties properties, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.issuer = properties.issuer();
        this.clock = clock;
    }

    /** A valid token, re-created a minute before the current one expires. */
    public String token() {
        CachedToken current = cached;
        Instant now = clock.instant();
        if (current == null || !now.isBefore(current.refreshAt())) {
            synchronized (this) {
                current = cached;
                if (current == null || !now.isBefore(current.refreshAt())) {
                    current = mint(now);
                    cached = current;
                }
            }
        }
        return current.value();
    }

    private CachedToken mint(Instant now) {
        Instant expiresAt = now.plus(LIFETIME);
        String token = Jwts.builder()
                .subject(SERVICE_ID.toString())
                .issuer(issuer)
                .claim(JwtValidator.CLAIM_EMAIL, SERVICE_EMAIL)
                .claim(JwtValidator.CLAIM_ROLE, SERVICE_ROLE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new CachedToken(token, expiresAt.minus(REFRESH_BEFORE_EXPIRY));
    }
}
