package com.ecommerce.productservice.dto.response;

import java.util.List;
import java.util.UUID;

/** A category with all its sub-categories nested, for menus: Electronics > [Mobiles, Laptops]. */
public record CategoryTreeNode(
        UUID id,
        String name,
        String description,
        List<CategoryTreeNode> children
) {
}
