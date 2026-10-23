package com.ecommerce.paymentservice.entity;

/**
 * <pre>
 * PENDING ──► SUCCEEDED ──► REFUNDED   (order cancelled after payment)
 *    │
 *    ▼
 * CANCELLED                            (order cancelled before payment, e.g. payment window expired)
 * </pre>
 * A declined attempt doesn't change the status: the customer can try again while it is PENDING.
 */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    CANCELLED,
    REFUNDED
}
