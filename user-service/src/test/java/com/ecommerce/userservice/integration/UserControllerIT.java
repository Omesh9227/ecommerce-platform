package com.ecommerce.userservice.integration;

import com.ecommerce.userservice.dto.request.UpdateProfileRequest;
import com.ecommerce.userservice.entity.AccountStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIT extends AbstractIntegrationTest {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.jwt.issuer}")
    private String jwtIssuer;

    @Test
    void getMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value(ME_URL));
    }

    @Test
    void getMe_withMalformedToken_returns401() throws Exception {
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer("garbage.token.value")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid access token"));
    }

    @Test
    void getMe_withExpiredToken_returns401() throws Exception {
        UUID userId = registerCustomer("omesh@example.com");
        Instant past = Instant.now().minusSeconds(7200);
        String expired = Jwts.builder()
                .subject(userId.toString())
                .issuer(jwtIssuer)
                .issuedAt(Date.from(past))
                .expiration(Date.from(past.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
                .compact();

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token has expired"));
    }

    @Test
    void getMe_withValidToken_returnsProfile() throws Exception {
        registerCustomer("omesh@example.com");
        String token = loginAndGetToken("omesh@example.com");

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("omesh@example.com"))
                .andExpect(jsonPath("$.data.firstName").value("Omesh"))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void getMe_afterAccountSuspended_returns401() throws Exception {
        registerCustomer("omesh@example.com");
        String token = loginAndGetToken("omesh@example.com");
        setStatus("omesh@example.com", AccountStatus.SUSPENDED);

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account is not active"));
    }

    @Test
    void updateMe_withValidData_updatesProfile() throws Exception {
        registerCustomer("omesh@example.com");
        String token = loginAndGetToken("omesh@example.com");

        mockMvc.perform(put(ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProfileRequest("Om", "Sharma", "9123456780"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile updated successfully"))
                .andExpect(jsonPath("$.data.firstName").value("Om"))
                .andExpect(jsonPath("$.data.lastName").value("Sharma"))
                .andExpect(jsonPath("$.data.phoneNumber").value("9123456780"))
                .andExpect(jsonPath("$.data.email").value("omesh@example.com"));
    }

    @Test
    void updateMe_withInvalidData_returns400() throws Exception {
        registerCustomer("omesh@example.com");
        String token = loginAndGetToken("omesh@example.com");

        mockMvc.perform(put(ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProfileRequest("", "Sharma", "12"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.firstName").exists())
                .andExpect(jsonPath("$.validationErrors.phoneNumber").exists());
    }
}
