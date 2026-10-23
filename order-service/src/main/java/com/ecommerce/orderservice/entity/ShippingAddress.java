package com.ecommerce.orderservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Delivery address, copied into the order (a later change to the user's address book never moves an order). */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShippingAddress {

    @Column(name = "ship_full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "ship_phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "ship_line1", nullable = false, length = 200)
    private String line1;

    @Column(name = "ship_line2", length = 200)
    private String line2;

    @Column(name = "ship_city", nullable = false, length = 100)
    private String city;

    @Column(name = "ship_state", nullable = false, length = 100)
    private String state;

    @Column(name = "ship_postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "ship_country", nullable = false, length = 2)
    private String country;
}
