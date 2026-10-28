package com.ecommerce.inventoryservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit record of one stock change. */
@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 20)
    private MovementType movementType;

    /** Change to on-hand stock (RESTOCK/ADJUSTMENT/SALE) or to reserved stock (RESERVE/RELEASE). */
    @Column(name = "quantity_change", nullable = false, updatable = false)
    private int quantityChange;

    @Column(name = "quantity_on_hand_after", nullable = false, updatable = false)
    private int quantityOnHandAfter;

    @Column(name = "quantity_reserved_after", nullable = false, updatable = false)
    private int quantityReservedAfter;

    @Column(name = "reason", updatable = false)
    private String reason;

    /** Order reference for RESERVE / RELEASE / SALE. */
    @Column(name = "reference", updatable = false, length = 100)
    private String reference;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
