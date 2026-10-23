package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.request.ProductRequest;
import com.ecommerce.productservice.dto.request.ProductSearchCriteria;
import com.ecommerce.productservice.dto.response.PageResponse;
import com.ecommerce.productservice.dto.response.ProductResponse;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductService {

    /** Customers only ever see ACTIVE products; admins see all and may filter by status. */
    PageResponse<ProductResponse> search(ProductSearchCriteria criteria, Pageable pageable, boolean admin);

    ProductResponse getProduct(UUID id, boolean admin);

    /**
     * The ACTIVE products among these ids (at most {@value #LOOKUP_MAX_IDS}), for other services such as the cart.
     * Unknown or hidden ids are simply left out, so the caller can tell which products are no longer for sale.
     */
    List<ProductResponse> lookup(Collection<UUID> ids);

    int LOOKUP_MAX_IDS = 50;

    ProductResponse create(ProductRequest request, UUID actorId);

    ProductResponse update(UUID id, ProductRequest request, UUID actorId);

    /** Soft delete: marks the product DISCONTINUED so existing orders can still reference it. */
    void delete(UUID id, UUID actorId);
}
