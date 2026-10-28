package com.ecommerce.apigateway.security;

import com.ecommerce.apigateway.config.JwtProperties;
import com.ecommerce.apigateway.support.TestTokens;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidatorTest {

    private final JwtValidator validator =
            new JwtValidator(new JwtProperties(TestTokens.SECRET, TestTokens.ISSUER));

    @Test
    void validToken_returnsClaims() {
        UUID userId = UUID.randomUUID();

        Claims claims = validator.validate(TestTokens.valid(userId, "omesh@example.com", "CUSTOMER"));

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get(JwtValidator.CLAIM_ROLE, String.class)).isEqualTo("CUSTOMER");
        assertThat(claims.get(JwtValidator.CLAIM_EMAIL, String.class)).isEqualTo("omesh@example.com");
    }

    @Test
    void expiredToken_isRejected() {
        assertThatThrownBy(() -> validator.validate(TestTokens.expired(UUID.randomUUID(), "CUSTOMER")))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenFromOtherKey_isRejected() {
        assertThatThrownBy(() -> validator.validate(TestTokens.signedWithOtherKey(UUID.randomUUID(), "CUSTOMER")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromOtherIssuer_isRejected() {
        assertThatThrownBy(() -> validator.validate(TestTokens.withIssuer("evil", UUID.randomUUID(), "ADMIN")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecret_failsFastAtStartup() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JwtValidator(new JwtProperties(shortSecret, TestTokens.ISSUER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }
}
