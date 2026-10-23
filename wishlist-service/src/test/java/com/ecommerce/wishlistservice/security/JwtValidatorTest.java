package com.ecommerce.wishlistservice.security;

import com.ecommerce.wishlistservice.config.JwtProperties;
import com.ecommerce.wishlistservice.support.TestTokens;
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

    private final JwtValidator validator = new JwtValidator(new JwtProperties(TestTokens.SECRET, TestTokens.ISSUER));

    @Test
    void validToken_returnsClaims() {
        UUID id = UUID.randomUUID();

        Claims claims = validator.validate(TestTokens.admin(id));

        assertThat(claims.getSubject()).isEqualTo(id.toString());
        assertThat(claims.get(JwtValidator.CLAIM_ROLE, String.class)).isEqualTo("ADMIN");
    }

    @Test
    void expiredToken_isRejected() {
        assertThatThrownBy(() -> validator.validate(TestTokens.expiredAdmin(UUID.randomUUID())))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenFromOtherKey_isRejected() {
        assertThatThrownBy(() -> validator.validate(TestTokens.signedWithOtherKey(UUID.randomUUID())))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecret_failsFastAtStartup() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JwtValidator(new JwtProperties(shortSecret, TestTokens.ISSUER)))
                .isInstanceOf(IllegalStateException.class);
    }
}
