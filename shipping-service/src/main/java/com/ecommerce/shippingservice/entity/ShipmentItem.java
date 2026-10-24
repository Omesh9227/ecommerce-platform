package com.ecommerce.shippingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A product in the parcel (what the warehouse picks). */
@Entity
@Table(name = "shipment_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, updatable = false)
    private Shipment shipment;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "sku", length = 64, updatable = false)
    private String sku;

    @Column(name = "product_name", length = 255, updatable = false)
    private String productName;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    ShipmentItem(Shipment shipment, UUID productId, String sku, String productName, int quantity) {
        this.shipment = shipment;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
    }
}
