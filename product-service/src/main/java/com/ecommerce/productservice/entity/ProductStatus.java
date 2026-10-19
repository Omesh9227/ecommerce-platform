package com.ecommerce.productservice.entity;

public enum ProductStatus {
    /** Visible to everyone. */
    ACTIVE,
    /** Temporarily hidden from customers (e.g. being edited). Visible to admins. */
    INACTIVE,
    /** Soft-deleted: hidden from customers, kept so past orders can still reference it. */
    DISCONTINUED
}
