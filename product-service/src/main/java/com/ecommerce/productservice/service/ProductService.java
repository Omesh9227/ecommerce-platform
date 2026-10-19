package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.request.ProductRequest;
import com.ecommerce.productservice.dto.request.ProductSearchCriteria;
import com.ecommerce.productservice.dto.response.PageResponse;
import com.ecommerce.productservice.dto.response.ProductResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductService {

    /** Customers only ever see ACTIVE products; admins see all and may filter by status. */
    PageResponse<ProductResponse> search(ProductSearchCriteria criteria, Pageable pageable, boolean admin);

    ProductResponse getProduct(UUID id, boolean admin);

    ProductResponse create(ProductRequest request, UUID actorId);

    ProductResponse update(UUID id, ProductRequest request, UUID actorId);

    /** Soft delete: marks the product DISCONTINUED so existing orders can still reference it. */
    void delete(UUID id, UUID actorId);
}
