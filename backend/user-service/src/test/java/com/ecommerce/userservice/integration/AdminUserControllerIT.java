package com.ecommerce.userservice.integration;

import com.ecommerce.userservice.dto.request.LoginRequest;
import com.ecommerce.userservice.dto.request.UpdateAccountStatusRequest;
import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerIT extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@example.com";

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUpAdmin() throws Exception {
        admin = createAdmin(ADMIN_EMAIL);
        adminToken = loginAndGetToken(ADMIN_EMAIL);
    }

    // ---------- Authorization ----------

    @Test
    void customer_accessingAdminApi_returns403() throws Exception {
        registerCustomer("omesh@example.com");
        String customerToken = loginAndGetToken("omesh@example.com");

        mockMvc.perform(get(ADMIN_USERS_URL).header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
    }

    @Test
    void anonymous_accessingAdminApi_returns401() throws Exception {
        mockMvc.perform(get(ADMIN_USERS_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_canAccessOwnProfileToo() throws Exception {
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    // ---------- List / get ----------

    @Test
    void admin_listUsers_returnsPage() throws Exception {
        registerCustomer("a@example.com");
        registerCustomer("b@example.com");

        mockMvc.perform(get(ADMIN_USERS_URL).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist());
    }

    @Test
    void admin_listUsers_filtersByRole() throws Exception {
        registerCustomer("a@example.com");
        registerCustomer("b@example.com");

        mockMvc.perform(get(ADMIN_USERS_URL).param("role", "CUSTOMER")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void admin_listUsers_invalidRoleFilter_returns400() throws Exception {
        mockMvc.perform(get(ADMIN_USERS_URL).param("role", "SUPERUSER")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'role'"));
    }

    @Test
    void admin_getUserById_returnsUser() throws Exception {
        UUID customerId = registerCustomer("omesh@example.com");

        mockMvc.perform(get(ADMIN_USERS_URL + "/" + customerId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(customerId.toString()))
                .andExpect(jsonPath("$.data.email").value("omesh@example.com"));
    }

    @Test
    void admin_getUnknownUser_returns404() throws Exception {
        mockMvc.perform(get(ADMIN_USERS_URL + "/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void admin_getUserWithMalformedId_returns400() throws Exception {
        mockMvc.perform(get(ADMIN_USERS_URL + "/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    // ---------- Status changes ----------

    @Test
    void admin_suspendsCustomer_customerCanNoLongerLogIn() throws Exception {
        UUID customerId = registerCustomer("omesh@example.com");

        mockMvc.perform(put(ADMIN_USERS_URL + "/" + customerId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateAccountStatusRequest(AccountStatus.SUSPENDED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("SUSPENDED"));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("omesh@example.com", VALID_PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_changingOwnStatus_returns400() throws Exception {
        mockMvc.perform(put(ADMIN_USERS_URL + "/" + admin.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateAccountStatusRequest(AccountStatus.SUSPENDED))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Admins cannot change their own account status"));
    }

    @Test
    void admin_invalidStatusValue_returns400() throws Exception {
        UUID customerId = registerCustomer("omesh@example.com");

        mockMvc.perform(put(ADMIN_USERS_URL + "/" + customerId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANNED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admin_missingStatus_returns400() throws Exception {
        UUID customerId = registerCustomer("omesh@example.com");

        mockMvc.perform(put(ADMIN_USERS_URL + "/" + customerId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.status").exists());
    }
}
