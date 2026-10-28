package com.ecommerce.orderservice.client;

/**
 * Another service answered with a 4xx: the request was understood but refused (e.g. not enough stock).
 * Callers decide what it means for the order; {@link #getMessage()} is the other service's own message.
 */
public class DownstreamRejectedException extends RuntimeException {

    private final String service;
    private final int status;

    public DownstreamRejectedException(String service, int status, String message) {
        super(message);
        this.service = service;
        this.status = status;
    }

    public String getService() {
        return service;
    }

    public int getStatus() {
        return status;
    }

    public boolean isConflict() {
        return status == 409;
    }

    public boolean isNotFound() {
        return status == 404;
    }
}
