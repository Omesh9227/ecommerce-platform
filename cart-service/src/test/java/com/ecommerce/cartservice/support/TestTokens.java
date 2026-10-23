package com.ecommerce.cartservice.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/** Builds JWTs exactly like user-service does, for cart-service tests. */
public final class TestTokens {

    /** Must match security.jwt.secret / issuer in src/test/resources/application-test.properties. */
    public static final String SECRET = "8JcdjF4BaCg1d9tJm+ONJ0g+J1cVkGcNWqfnfT5bRkD1eMTyx6g4jH5gqi0C7f/L";
    public static final String ISSUER = "ecommerce-user-service";

    private TestTokens() {
    }

    public static String admin(UUID userId) {
        return build(SECRET, ISSUER, userId, "admin@ecommerce.com", "ADMIN", Instant.now().plusSeconds(3600));
    }

    public static String customer(UUID userId) {
        return build(SECRET, ISSUER, userId, "omesh@example.com", "CUSTOMER", Instant.now().plusSeconds(3600));
    }

    public static String expiredAdmin(UUID userId) {
        return build(SECRET, ISSUER, userId, "admin@ecommerce.com", "ADMIN", Instant.now().minusSeconds(60));
    }

    public static String signedWithOtherKey(UUID userId) {
        String otherSecret = Base64.getEncoder()
                .encodeToString("a-completely-different-secret-key-of-enough-length".getBytes(StandardCharsets.UTF_8));
        return build(otherSecret, ISSUER, userId, "admin@ecommerce.com", "ADMIN", Instant.now().plusSeconds(3600));
    }

    private static String build(String secret, String issuer, UUID userId, String email, String role, Instant expiresAt) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(expiresAt.minusSeconds(3600)))
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .compact();
    }
}
