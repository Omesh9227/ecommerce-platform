package com.ecommerce.productservice.service.impl;

import com.ecommerce.productservice.dto.request.ProductRequest;
import com.ecommerce.productservice.dto.request.ProductSearchCriteria;
import com.ecommerce.productservice.dto.response.PageResponse;
import com.ecommerce.productservice.dto.response.ProductResponse;
import com.ecommerce.productservice.entity.Category;
import com.ecommerce.productservice.entity.Product;
import com.ecommerce.productservice.entity.ProductStatus;
import com.ecommerce.productservice.exception.BadRequestException;
import com.ecommerce.productservice.exception.ConflictException;
import com.ecommerce.productservice.exception.ResourceNotFoundException;
import com.ecommerce.productservice.mapper.ProductMapper;
import com.ecommerce.productservice.outbox.ProductEventPublisher;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import com.ecommerce.productservice.repository.ProductSpecifications;
import com.ecommerce.productservice.service.CategoryService;
import com.ecommerce.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String PRODUCT = "Product";

    /** Only these fields can be used in ?sort=... (anything else is a 400, not a 500). */
    static final Set<String> SORTABLE_FIELDS = Set.of("name", "brand", "price", "sellingPrice", "availableQuantity", "createdAt", "updatedAt");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final ProductMapper productMapper;
    private final ProductEventPublisher eventPublisher;

    // ---------- Read ----------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(ProductSearchCriteria criteria, Pageable pageable, boolean admin) {
        validateSort(pageable.getSort());
        validatePriceRange(criteria.minPrice(), criteria.maxPrice());

        Set<UUID> categoryIds = null;
        if (criteria.categoryId() != null) {
            categoryIds = categoryService.getSelfAndDescendantIds(criteria.categoryId());
            if (categoryIds.isEmpty()) {
                // Unknown category: nothing can match
                return PageResponse.from(new PageImpl<ProductResponse>(List.of(), pageable, 0));
            }
        }

        ProductStatus status = admin ? criteria.status() : ProductStatus.ACTIVE;
        Page<Product> page = productRepository.findAll(
                ProductSpecifications.withFilters(status, categoryIds, criteria.brand(), criteria.search(),
                        criteria.minPrice(), criteria.maxPrice(), criteria.inStock()),
                pageable);

        return PageResponse.from(page.map(productMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id, boolean admin) {
        Product product = findProduct(id);
        if (!admin && product.getStatus() != ProductStatus.ACTIVE) {
            // To customers a hidden product simply doesn't exist
            throw new ResourceNotFoundException(PRODUCT, id);
        }
        return productMapper.toResponse(product);
    }

    // ---------- Write ----------

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request, UUID actorId) {
        String sku = normalizeSku(request.sku());
        if (productRepository.existsBySku(sku)) {
            throw duplicateSku(sku);
        }

        Product product = new Product();
        product.setSku(sku);
        applyRequest(product, request);
        product.setStatus(request.status() != null ? request.status() : ProductStatus.ACTIVE);
        product.setCreatedBy(actorId);
        product.setUpdatedBy(actorId);

        Product saved = saveAndFlush(product);
        eventPublisher.productCreated(saved);
        log.info("Product created id={} sku={} by={}", saved.getId(), sku, actorId);
        return productMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request, UUID actorId) {
        Product product = findProduct(id);

        String sku = normalizeSku(request.sku());
        if (!sku.equals(product.getSku()) && productRepository.existsBySkuAndIdNot(sku, id)) {
            throw duplicateSku(sku);
        }

        product.setSku(sku);
        applyRequest(product, request);
        if (request.status() != null) {
            product.setStatus(request.status());
        }
        product.setUpdatedBy(actorId);

        Product saved = saveAndFlush(product);
        eventPublisher.productUpdated(saved);
        log.info("Product updated id={} by={}", id, actorId);
        return productMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID actorId) {
        Product product = findProduct(id);
        if (product.getStatus() == ProductStatus.DISCONTINUED) {
            return; // already deleted: DELETE is idempotent
        }
        product.setStatus(ProductStatus.DISCONTINUED);
        product.setUpdatedBy(actorId);
        Product saved = productRepository.saveAndFlush(product);
        eventPublisher.productDiscontinued(saved);
        log.info("Product discontinued id={} by={}", id, actorId);
    }

    // ---------- Helpers ----------

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setDescription(trimToNull(request.description()));
        product.setBrand(trimToNull(request.brand()));
        product.setCategory(findCategoryForProduct(request.categoryId()));
        product.updatePricing(request.price(), request.discountPercent());
        product.replaceImageUrls(request.imageUrls() == null ? List.of()
                : request.imageUrls().stream().map(String::trim).toList());
    }

    private Category findCategoryForProduct(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BadRequestException("Category not found with id: " + categoryId));
    }

    private Product findProduct(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(PRODUCT, id));
    }

    private Product saveAndFlush(Product product) {
        try {
            return productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent create with the same SKU: the unique constraint catches what the check above missed
            throw duplicateSku(product.getSku());
        }
    }

    private static void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'. Allowed: "
                        + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()));
            }
        }
    }

    private static void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)) {
            throw new BadRequestException("Price filters cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice cannot be greater than maxPrice");
        }
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private static ConflictException duplicateSku(String sku) {
        return new ConflictException("A product with SKU '" + sku + "' already exists");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
