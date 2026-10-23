package com.ecommerce.inventoryservice.entity;

public enum MovementType {
    /** Admin set or corrected the on-hand quantity (stock count, damage, ...). */
    ADJUSTMENT,
    /** New stock arrived. */
    RESTOCK,
    /** Stock held for an order. */
    RESERVE,
    /** A hold was cancelled; stock is available again. */
    RELEASE,
    /** A held order was confirmed; stock left the warehouse. */
    SALE,
    /** A sold order was cancelled before shipping; its stock is back on hand. */
    RETURN
}
