package com.ecommerce.userservice.service.impl;

import com.ecommerce.userservice.dto.request.UpdateProfileRequest;
import com.ecommerce.userservice.dto.response.UserResponse;
import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.mapper.UserMapper;
import com.ecommerce.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private UserServiceImpl userService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, new UserMapper());
    }

    private User user() {
        return User.builder()
                .id(userId)
                .firstName("Omesh")
                .lastName("Kumar")
                .email("omesh@example.com")
                .phoneNumber("9876543210")
                .passwordHash("hashed")
                .role(Role.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void getCurrentUser_returnsProfile() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));

        UserResponse response = userService.getCurrentUser(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("omesh@example.com");
        assertThat(response.firstName()).isEqualTo("Omesh");
    }

    @Test
    void getCurrentUser_missingUser_throwsNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser(userId)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateCurrentUser_updatesEditableFieldsOnly() {
        User existing = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.saveAndFlush(existing)).thenReturn(existing);

        UserResponse response = userService.updateCurrentUser(userId,
                new UpdateProfileRequest(" Om ", "Sharma", "9123456780"));

        assertThat(response.firstName()).isEqualTo("Om");
        assertThat(response.lastName()).isEqualTo("Sharma");
        assertThat(response.phoneNumber()).isEqualTo("9123456780");
        assertThat(response.email()).isEqualTo("omesh@example.com");
        assertThat(existing.getPasswordHash()).isEqualTo("hashed");
    }
}
