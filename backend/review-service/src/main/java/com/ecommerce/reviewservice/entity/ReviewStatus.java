package com.ecommerce.reviewservice.entity;

/** Hidden reviews (by an admin) are not shown and don't count towards the product's rating. */
public enum ReviewStatus {
    VISIBLE,
    HIDDEN
}
