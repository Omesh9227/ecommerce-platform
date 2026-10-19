package com.ecommerce.userservice.service.impl;

import com.ecommerce.userservice.dto.request.UpdateAccountStatusRequest;
import com.ecommerce.userservice.dto.response.PageResponse;
import com.ecommerce.userservice.dto.response.UserResponse;
import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.exception.InvalidOperationException;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.mapper.UserMapper;
import com.ecommerce.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private AdminUserServiceImpl adminUserService;

    private final UUID adminId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserServiceImpl(userRepository, new UserMapper());
    }

    private User customer() {
        return User.builder()
                .id(customerId)
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
    @SuppressWarnings("unchecked")
    void listUsers_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(customer()), pageable, 1));

        PageResponse<UserResponse> page = adminUserService.listUsers(Role.CUSTOMER, null, pageable);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).extracting(UserResponse::email).containsExactly("omesh@example.com");
    }

    @Test
    void updateAccountStatus_changesStatus() {
        User customer = customer();
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.saveAndFlush(customer)).thenReturn(customer);

        UserResponse response = adminUserService.updateAccountStatus(adminId, customerId,
                new UpdateAccountStatusRequest(AccountStatus.SUSPENDED));

        assertThat(response.accountStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void updateAccountStatus_ownAccount_isRejected() {
        assertThatThrownBy(() -> adminUserService.updateAccountStatus(adminId, adminId,
                new UpdateAccountStatusRequest(AccountStatus.SUSPENDED)))
                .isInstanceOf(InvalidOperationException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void updateAccountStatus_unknownUser_throwsNotFound() {
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.updateAccountStatus(adminId, customerId,
                new UpdateAccountStatusRequest(AccountStatus.SUSPENDED)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getUser_unknownUser_throwsNotFound() {
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUser(customerId)).isInstanceOf(UserNotFoundException.class);
    }
}
