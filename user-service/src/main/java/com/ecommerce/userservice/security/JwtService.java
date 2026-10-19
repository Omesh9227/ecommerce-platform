package com.ecommerce.userservice.security;

import com.ecommerce.userservice.config.JwtProperties;
import com.ecommerce.userservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and validates HS256-signed access tokens.
 * Token layout: sub = userId, email, role, iss, iat, exp.
 */
@Service
public class JwtService {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";
    private static final int MIN_KEY_BYTES = 32; // 256 bits, required for HS256

    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final String issuer;

    public JwtService(JwtProperties properties) {
        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "security.jwt.secret must be a Base64-encoded key of at least 256 bits (32 bytes)");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationSeconds = properties.expirationSeconds();
        this.issuer = properties.issuer();
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(issuer)
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies signature, expiry and issuer, and returns the claims.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException if the token has expired
     * @throws JwtException                        for any other invalid token
     * @throws IllegalArgumentException            if the token is null or empty
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
