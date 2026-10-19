package com.ecommerce.userservice.exception;

import org.springframework.http.HttpStatus;

/** Same message for unknown email and wrong password, so attackers can't discover which emails are registered. */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
