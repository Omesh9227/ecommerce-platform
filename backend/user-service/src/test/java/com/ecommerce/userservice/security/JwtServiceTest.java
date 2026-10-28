package com.ecommerce.userservice.security;

import com.ecommerce.userservice.config.JwtProperties;
import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "8JcdjF4BaCg1d9tJm+ONJ0g+J1cVkGcNWqfnfT5bRkD1eMTyx6g4jH5gqi0C7f/L";
    private static final String ISSUER = "test-issuer";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 3600, ISSUER));

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("omesh@example.com")
                .role(Role.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void generatedToken_containsUserClaims() {
        User user = user();

        Claims claims = jwtService.parseAndValidate(jwtService.generateAccessToken(user));

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get(JwtService.CLAIM_EMAIL, String.class)).isEqualTo("omesh@example.com");
        assertThat(claims.get(JwtService.CLAIM_ROLE, String.class)).isEqualTo("CUSTOMER");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getExpiration()).isAfter(new Date());
        assertThat(jwtService.getExpirationSeconds()).isEqualTo(3600);
    }

    @Test
    void expiredToken_isRejected() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        Instant past = Instant.now().minusSeconds(7200);
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .issuedAt(Date.from(past))
                .expiration(Date.from(past.plusSeconds(60)))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAndValidate(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithDifferentKey_isRejected() {
        String otherSecret = Base64.getEncoder()
                .encodeToString("a-completely-different-secret-key-of-enough-length".getBytes(StandardCharsets.UTF_8));
        JwtService otherService = new JwtService(new JwtProperties(otherSecret, 3600, ISSUER));
        String forged = otherService.generateAccessToken(user());

        assertThatThrownBy(() -> jwtService.parseAndValidate(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromDifferentIssuer_isRejected() {
        JwtService otherIssuer = new JwtService(new JwtProperties(SECRET, 3600, "someone-else"));
        String token = otherIssuer.generateAccessToken(user());

        assertThatThrownBy(() -> jwtService.parseAndValidate(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void malformedToken_isRejected() {
        assertThatThrownBy(() -> jwtService.parseAndValidate("not.a.jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecret_failsFastAtStartup() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JwtService(new JwtProperties(shortSecret, 3600, ISSUER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }
}
