package com.ecommerce.inventoryservice.security;

import com.ecommerce.inventoryservice.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/** Verifies tokens issued by user-service: signature (same JWT_SECRET), expiry and issuer. */
@Component
public class JwtValidator {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey signingKey;
    private final String issuer;

    public JwtValidator(JwtProperties properties) {
        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "security.jwt.secret must be a Base64-encoded key of at least 256 bits (32 bytes)");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = properties.issuer();
    }

    /**
     * @throws io.jsonwebtoken.ExpiredJwtException if the token has expired
     * @throws io.jsonwebtoken.JwtException        for any other invalid token
     * @throws IllegalArgumentException            if the token is empty
     */
    public Claims validate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
