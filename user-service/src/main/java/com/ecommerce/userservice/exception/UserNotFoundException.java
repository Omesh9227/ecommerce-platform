package com.ecommerce.userservice.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException(UUID userId) {
        super(HttpStatus.NOT_FOUND, "User not found with id: " + userId);
    }
}
