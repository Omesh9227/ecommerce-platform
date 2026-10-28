package com.ecommerce.userservice.mapper;

import com.ecommerce.userservice.dto.response.RegisterResponse;
import com.ecommerce.userservice.dto.response.UserResponse;
import com.ecommerce.userservice.dto.response.UserSummaryResponse;
import com.ecommerce.userservice.entity.User;
import org.springframework.stereotype.Component;

/** Entity → DTO mapping. Hand-written so the password hash can never leak through an auto-mapped field. */
@Component
public class UserMapper {

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getAccountStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(user.getId(), user.getEmail(), user.getRole());
    }

    public RegisterResponse toRegisterResponse(User user) {
        return new RegisterResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
