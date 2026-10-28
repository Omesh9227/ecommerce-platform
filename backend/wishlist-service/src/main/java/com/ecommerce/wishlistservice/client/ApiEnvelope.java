package com.ecommerce.wishlistservice.client;

/** The { success, message, data } envelope every service in this platform returns. */
public record ApiEnvelope<T>(boolean success, String message, T data) {
}
