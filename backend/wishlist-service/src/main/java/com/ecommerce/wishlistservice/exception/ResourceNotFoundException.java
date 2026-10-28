package com.ecommerce.wishlistservice.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, UUID id) {
        super(HttpStatus.NOT_FOUND, resource + " not found with id: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
