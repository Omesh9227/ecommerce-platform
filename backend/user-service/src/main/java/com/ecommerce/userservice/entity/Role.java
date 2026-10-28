package com.ecommerce.userservice.entity;

public enum Role {
    CUSTOMER,
    ADMIN;

    /** Spring Security authority name, e.g. ROLE_CUSTOMER. */
    public String authority() {
        return "ROLE_" + name();
    }
}
