package com.ecommerce.userservice.exception;

import com.ecommerce.userservice.entity.AccountStatus;
import org.springframework.http.HttpStatus;

public class AccountNotActiveException extends ApiException {

    public AccountNotActiveException(AccountStatus status) {
        super(HttpStatus.FORBIDDEN, "Account is " + status.name().toLowerCase() + ". Please contact support.");
    }
}
