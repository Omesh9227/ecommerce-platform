package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.request.UpdateProfileRequest;
import com.ecommerce.userservice.dto.response.UserResponse;

import java.util.UUID;

public interface UserService {

    UserResponse getCurrentUser(UUID userId);

    UserResponse updateCurrentUser(UUID userId, UpdateProfileRequest request);
}
