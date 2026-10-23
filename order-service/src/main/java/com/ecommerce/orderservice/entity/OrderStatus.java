package com.ecommerce.orderservice.entity;

/**
 * Order lifecycle:
 * <pre>
 * CREATED ──► PENDING_PAYMENT ──► CONFIRMED ──► SHIPPED ──► DELIVERED
 *    │              │
 *    ▼              ▼
 * CHECKOUT_FAILED  CANCELLED   (customer, admin, or payment not received in time)
 * </pre>
 * CREATED is internal: the order row exists but stock is not reserved yet. Customers never see CREATED or
 * CHECKOUT_FAILED orders in their order list.
 */
public enum OrderStatus {
    CREATED,
    PENDING_PAYMENT,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    CHECKOUT_FAILED;

    public boolean canMoveTo(OrderStatus next) {
        return switch (this) {
            case CREATED -> next == PENDING_PAYMENT || next == CHECKOUT_FAILED;
            case PENDING_PAYMENT -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == SHIPPED;
            case SHIPPED -> next == DELIVERED;
            case DELIVERED, CANCELLED, CHECKOUT_FAILED -> false;
        };
    }

    public boolean isVisibleToCustomer() {
        return this != CREATED && this != CHECKOUT_FAILED;
    }
}
