package com.ecommerce.shippingservice.repository;

import com.ecommerce.shippingservice.entity.Shipment;
import com.ecommerce.shippingservice.entity.ShipmentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/** Filters for shipment lists. A null value means "no filter". */
public final class ShipmentSpecifications {

    private ShipmentSpecifications() {
    }

    public static Specification<Shipment> belongsTo(UUID userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("userId"), userId);
    }

    public static Specification<Shipment> hasStatus(ShipmentStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Shipment> forOrder(String orderNumber) {
        return (root, query, cb) -> orderNumber == null || orderNumber.isBlank() ? null
                : cb.equal(root.get("orderNumber"), orderNumber.trim().toUpperCase());
    }

    public static Specification<Shipment> withCarrier(String carrier) {
        return (root, query, cb) -> carrier == null || carrier.isBlank() ? null
                : cb.equal(cb.lower(root.get("carrier")), carrier.trim().toLowerCase());
    }
}
