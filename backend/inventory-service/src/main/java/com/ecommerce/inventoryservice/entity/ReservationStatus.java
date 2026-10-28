package com.ecommerce.inventoryservice.entity;

public enum ReservationStatus {
    /** Stock is held for the order. */
    RESERVED,
    /** Hold cancelled (order cancelled / payment failed); stock is available again. */
    RELEASED,
    /** Order completed; held stock was deducted from on-hand. */
    CONFIRMED,
    /** A confirmed (paid) order was cancelled; its stock was added back to on-hand. */
    RETURNED
}
