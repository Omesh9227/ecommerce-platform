package com.ecommerce.productservice.mapper;

import com.ecommerce.productservice.config.CatalogProperties;
import com.ecommerce.productservice.dto.response.CategorySummary;
import com.ecommerce.productservice.dto.response.ProductResponse;
import com.ecommerce.productservice.entity.Category;
import com.ecommerce.productservice.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Entity → DTO. Must be called inside a transaction (category and images are lazy). */
@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final CatalogProperties catalogProperties;

    public ProductResponse toResponse(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getBrand(),
                new CategorySummary(category.getId(), category.getName()),
                product.getPrice(),
                product.getDiscountPercent(),
                product.getSellingPrice(),
                catalogProperties.currency(),
                product.getStatus(),
                product.getAvailableQuantity(),
                product.getAvailableQuantity() > 0,
                product.getAverageRating() == null ? BigDecimal.ZERO.setScale(1) : product.getAverageRating(),
                product.getReviewCount(),
                List.copyOf(product.getImageUrls()),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
