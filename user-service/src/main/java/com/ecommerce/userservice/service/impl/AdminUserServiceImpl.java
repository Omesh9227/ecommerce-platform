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
import com.ecommerce.userservice.repository.UserSpecifications;
import com.ecommerce.userservice.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Role role, AccountStatus status, Pageable pageable) {
        return PageResponse.from(
                userRepository.findAll(UserSpecifications.withFilters(role, status), pageable)
                        .map(userMapper::toUserResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        return userMapper.toUserResponse(findUser(userId));
    }

    @Override
    @Transactional
    public UserResponse updateAccountStatus(UUID actingAdminId, UUID userId, UpdateAccountStatusRequest request) {
        if (actingAdminId.equals(userId)) {
            throw new InvalidOperationException("Admins cannot change their own account status");
        }

        User user = findUser(userId);
        AccountStatus previous = user.getAccountStatus();
        user.setAccountStatus(request.status());
        User saved = userRepository.saveAndFlush(user);

        log.info("Admin {} changed status of user {} from {} to {}", actingAdminId, userId, previous, request.status());
        return userMapper.toUserResponse(saved);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }
}
