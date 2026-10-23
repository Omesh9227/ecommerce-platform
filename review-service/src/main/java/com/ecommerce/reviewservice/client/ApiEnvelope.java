package com.ecommerce.reviewservice.client;

/** The { success, message, data } envelope every service in this platform returns. */
public record ApiEnvelope<T>(boolean success, String message, T data) {
}
