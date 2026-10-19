package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * "Parent_Id" (with underscore) tells Spring Data explicitly to follow category.parent.id.
 *
 * Sibling-name checks are separate methods for top-level vs child categories on purpose:
 * it avoids "parentId IS NULL OR ..." queries with null parameters, which PostgreSQL can't type.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByParent_Id(UUID parentId);

    boolean existsByParentIsNullAndNameIgnoreCase(String name);

    boolean existsByParentIsNullAndNameIgnoreCaseAndIdNot(String name, UUID excludeId);

    boolean existsByParent_IdAndNameIgnoreCase(UUID parentId, String name);

    boolean existsByParent_IdAndNameIgnoreCaseAndIdNot(UUID parentId, String name, UUID excludeId);
}
