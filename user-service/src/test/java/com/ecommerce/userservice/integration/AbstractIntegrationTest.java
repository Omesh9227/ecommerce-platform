package com.ecommerce.userservice.integration;

import com.ecommerce.userservice.dto.request.LoginRequest;
import com.ecommerce.userservice.dto.request.RegisterRequest;
import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context + real PostgreSQL (ecommerce_user_test_db) + the real security filter chain.
 * Testcontainers will replace the local test database once Docker is introduced.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String VALID_PASSWORD = "Password@123";
    protected static final String REGISTER_URL = "/api/v1/auth/register";
    protected static final String LOGIN_URL = "/api/v1/auth/login";
    protected static final String ME_URL = "/api/v1/users/me";
    protected static final String ADMIN_USERS_URL = "/api/v1/admin/users";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected RegisterRequest registerRequest(String email) {
        return new RegisterRequest("Omesh", "Kumar", email, "9876543210", VALID_PASSWORD);
    }

    /** Registers a customer through the real API and returns its id. */
    protected UUID registerCustomer(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(registerRequest(email))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).at("/data/userId").asText());
    }

    /** Admins can't self-register, so they are inserted directly (as the bootstrap runner does). */
    protected User createAdmin(String email) {
        return userRepository.save(User.builder()
                .firstName("Admin")
                .lastName("User")
                .email(email)
                .phoneNumber("9999999999")
                .passwordHash(passwordEncoder.encode(VALID_PASSWORD))
                .role(Role.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    protected String loginAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, VALID_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).at("/data/accessToken").asText();
    }

    protected void setStatus(String email, AccountStatus status) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAccountStatus(status);
        userRepository.save(user);
    }
}
