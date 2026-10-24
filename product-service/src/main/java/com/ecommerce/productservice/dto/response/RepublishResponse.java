package com.ecommerce.productservice.dto.response;

/** How many catalog events were queued for sending again. */
public record RepublishResponse(int categories, int products) {
}
