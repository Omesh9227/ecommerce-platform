package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.request.UpdateAccountStatusRequest;
import com.ecommerce.userservice.dto.response.PageResponse;
import com.ecommerce.userservice.dto.response.UserResponse;
import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.Role;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminUserService {

    PageResponse<UserResponse> listUsers(Role role, AccountStatus status, Pageable pageable);

    UserResponse getUser(UUID userId);

    UserResponse updateAccountStatus(UUID actingAdminId, UUID userId, UpdateAccountStatusRequest request);
}
