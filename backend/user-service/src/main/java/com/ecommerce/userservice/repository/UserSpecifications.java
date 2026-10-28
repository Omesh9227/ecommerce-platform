package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Dynamic filters for the admin user listing. Null filters are ignored. */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> withFilters(Role role, AccountStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("accountStatus"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
