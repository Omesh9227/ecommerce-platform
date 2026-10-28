package com.ecommerce.userservice.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends ApiException {

    public UserAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "An account with this email already exists");
    }
}
