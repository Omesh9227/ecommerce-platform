package com.ecommerce.orderservice.dto.response;

public record ShippingAddressResponse(
        String fullName,
        String phone,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country
) {
}
