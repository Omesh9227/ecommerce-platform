package com.ecommerce.orderservice.entity;

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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** One product in an order, frozen at checkout (price, name and SKU never change afterwards). */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "sku", nullable = false, updatable = false, length = 64)
    private String sku;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "image_url", updatable = false, length = 1000)
    private String imageUrl;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "list_price", updatable = false, precision = 12, scale = 2)
    private BigDecimal listPrice;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    public OrderItem(UUID productId, String sku, String productName, String imageUrl,
                     BigDecimal unitPrice, BigDecimal listPrice, int quantity) {
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.imageUrl = imageUrl;
        this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        this.listPrice = listPrice == null ? null : listPrice.setScale(2, RoundingMode.HALF_UP);
        this.quantity = quantity;
        this.lineTotal = this.unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    void attachTo(CustomerOrder order) {
        this.order = order;
    }
}
