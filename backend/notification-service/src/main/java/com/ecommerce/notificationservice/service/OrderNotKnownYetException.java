package com.ecommerce.notificationservice.service;

/**
 * A payment event arrived before its order's ORDER_PLACED event (they travel on different topics, so Kafka gives no
 * ordering between them). Retryable: by the next attempt the order is normally known.
 */
public class OrderNotKnownYetException extends RuntimeException {

    public OrderNotKnownYetException(String orderNumber) {
        super("Order " + orderNumber + " is not known yet; will retry");
    }
}
