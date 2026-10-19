package com.ecommerce.userservice.service.impl;

import com.ecommerce.userservice.dto.request.LoginRequest;
import com.ecommerce.userservice.dto.request.RegisterRequest;
import com.ecommerce.userservice.dto.response.LoginResponse;
import com.ecommerce.userservice.dto.response.RegisterResponse;
import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.exception.AccountNotActiveException;
import com.ecommerce.userservice.exception.InvalidCredentialsException;
import com.ecommerce.userservice.exception.UserAlreadyExistsException;
import com.ecommerce.userservice.mapper.UserMapper;
import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PASSWORD = "Password@123";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, new UserMapper());
    }

    private RegisterRequest registerRequest(String email) {
        return new RegisterRequest("Omesh", "Kumar", email, "9876543210", PASSWORD);
    }

    private User user(AccountStatus status) {
        return User.builder()
                .id(UUID.randomUUID())
                .firstName("Omesh")
                .lastName("Kumar")
                .email("omesh@example.com")
                .phoneNumber("9876543210")
                .passwordHash("hashed")
                .role(Role.CUSTOMER)
                .accountStatus(status)
                .build();
    }

    // ---------- register ----------

    @Test
    void register_createsActiveCustomerWithHashedPasswordAndNormalisedEmail() {
        when(userRepository.existsByEmail("omesh@example.com")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User toSave = invocation.getArgument(0);
            toSave.setId(UUID.randomUUID());
            return toSave;
        });

        RegisterResponse response = authService.register(registerRequest("  Omesh@Example.COM "));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("omesh@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

        assertThat(response.userId()).isNotNull();
        assertThat(response.email()).isEqualTo("omesh@example.com");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("omesh@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest("omesh@example.com")))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_concurrentDuplicateCaughtByUniqueConstraint_throwsConflict() {
        when(userRepository.existsByEmail("omesh@example.com")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() -> authService.register(registerRequest("omesh@example.com")))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    // ---------- login ----------

    @Test
    void login_validCredentials_returnsToken() {
        User user = user(AccountStatus.ACTIVE);
        when(userRepository.findByEmail("omesh@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(new LoginRequest("Omesh@Example.com", PASSWORD));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.user().userId()).isEqualTo(user.getId());
        assertThat(response.user().role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        when(userRepository.findByEmail("omesh@example.com")).thenReturn(Optional.of(user(AccountStatus.ACTIVE)));
        when(passwordEncoder.matches("Wrong@123", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("omesh@example.com", "Wrong@123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verifyNoInteractions(jwtService);
    }

    @Test
    void login_unknownEmail_throwsSameInvalidCredentials() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verifyNoInteractions(jwtService);
    }

    @Test
    void login_suspendedAccount_throwsAccountNotActive() {
        when(userRepository.findByEmail("omesh@example.com")).thenReturn(Optional.of(user(AccountStatus.SUSPENDED)));
        when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("omesh@example.com", PASSWORD)))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("suspended");
        verifyNoInteractions(jwtService);
    }
}
