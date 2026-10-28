package com.ecommerce.orderservice.security;

import com.ecommerce.orderservice.config.JwtProperties;
import com.ecommerce.orderservice.support.MutableClock;
import com.ecommerce.orderservice.support.TestTokens;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenProviderTest {

    private final JwtProperties properties = new JwtProperties(TestTokens.SECRET, TestTokens.ISSUER);

    @Test
    void token_isAValidServiceToken_reusedUntilShortlyBeforeExpiry() {
        MutableClock clock = new MutableClock();
        ServiceTokenProvider provider = new ServiceTokenProvider(properties, clock);

        String first = provider.token();
        Claims claims = new JwtValidator(properties).validate(first);
        assertThat(claims.getSubject()).isEqualTo(ServiceTokenProvider.SERVICE_ID.toString());
        assertThat(claims.get(JwtValidator.CLAIM_ROLE, String.class)).isEqualTo("SERVICE");
        assertThat(claims.getIssuer()).isEqualTo(TestTokens.ISSUER);

        clock.advance(Duration.ofMinutes(3));
        assertThat(provider.token()).isSameAs(first);

        clock.advance(Duration.ofMinutes(2)); // within a minute of expiry
        assertThat(provider.token()).isNotEqualTo(first);
    }
}
