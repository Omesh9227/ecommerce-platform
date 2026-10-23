package com.ecommerce.inventoryservice.entity;

public enum ReservationStatus {
    /** Stock is held for the order. */
    RESERVED,
    /** Hold cancelled (order cancelled / payment failed); stock is available again. */
    RELEASED,
    /** Order completed; held stock was deducted from on-hand. */
    CONFIRMED
}
