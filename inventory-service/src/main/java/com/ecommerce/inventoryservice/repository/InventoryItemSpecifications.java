package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.entity.InventoryItem;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filters for the admin inventory list. */
public final class InventoryItemSpecifications {

    private static final char ESCAPE = '\\';

    private InventoryItemSpecifications() {
    }

    public static Specification<InventoryItem> withFilters(Boolean lowStock, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (lowStock != null) {
                Expression<Integer> available = cb.diff(root.<Integer>get("quantityOnHand"),
                        root.<Integer>get("quantityReserved"));
                Expression<Integer> threshold = root.get("lowStockThreshold");
                predicates.add(lowStock ? cb.lessThanOrEqualTo(available, threshold)
                        : cb.greaterThan(available, threshold));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("sku")), pattern, ESCAPE),
                        cb.like(cb.lower(root.get("productName")), pattern, ESCAPE)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
