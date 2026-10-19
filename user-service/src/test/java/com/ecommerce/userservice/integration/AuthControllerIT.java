package com.ecommerce.userservice.integration;

import com.ecommerce.userservice.dto.request.LoginRequest;
import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends AbstractIntegrationTest {

    // ---------- Registration ----------

    @Test
    void register_success_returns201AndStoresHashedPassword() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registerRequest("Omesh@Example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value("omesh@example.com"))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        User stored = userRepository.findByEmail("omesh@example.com").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(VALID_PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(VALID_PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(stored.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(stored.getCreatedAt()).isNotNull();
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        registerCustomer("omesh@example.com");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registerRequest("OMESH@example.com"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("An account with this email already exists"))
                .andExpect(jsonPath("$.path").value(REGISTER_URL));
    }

    @Test
    void register_invalidEmail_returns400WithFieldError() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registerRequest("not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.validationErrors.email").exists());
    }

    @Test
    void register_weakPassword_returns400WithFieldError() throws Exception {
        String body = """
                {"firstName":"Omesh","lastName":"Kumar","email":"omesh@example.com",
                 "phoneNumber":"9876543210","password":"password"}
                """;

        mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    void register_missingFields_returns400ForEachField() throws Exception {
        mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.firstName").exists())
                .andExpect(jsonPath("$.validationErrors.lastName").exists())
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.phoneNumber").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    void register_malformedJson_returns400() throws Exception {
        mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body or invalid field value"));
    }

    // ---------- Login ----------

    @Test
    void login_success_returnsBearerToken() throws Exception {
        registerCustomer("omesh@example.com");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("omesh@example.com", VALID_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.accessToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.user.email").value("omesh@example.com"))
                .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        registerCustomer("omesh@example.com");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("omesh@example.com", "Wrong@1234"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_unknownUser_returnsSame401() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("nobody@example.com", VALID_PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_suspendedAccount_returns403() throws Exception {
        registerCustomer("omesh@example.com");
        setStatus("omesh@example.com", AccountStatus.SUSPENDED);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("omesh@example.com", VALID_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
