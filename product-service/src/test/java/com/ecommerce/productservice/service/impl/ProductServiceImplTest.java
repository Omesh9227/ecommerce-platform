package com.ecommerce.productservice.service.impl;

import com.ecommerce.productservice.config.CatalogProperties;
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
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import com.ecommerce.productservice.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CategoryService categoryService;

    private ProductServiceImpl productService;

    private final UUID adminId = UUID.randomUUID();
    private Category mobiles;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productRepository, categoryRepository, categoryService,
                new ProductMapper(new CatalogProperties("INR")));
        mobiles = Category.builder().id(UUID.randomUUID()).name("Mobiles").build();
    }

    private ProductRequest request(String sku, String price, String discount, ProductStatus status) {
        return new ProductRequest(sku, "Apple iPhone 15", "48MP camera", "Apple", mobiles.getId(),
                new BigDecimal(price), discount == null ? null : new BigDecimal(discount), status,
                List.of("https://cdn.example.com/iphone.jpg"));
    }

    private Product existingProduct(ProductStatus status) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setSku("IPHONE-15");
        product.setName("Apple iPhone 15");
        product.setCategory(mobiles);
        product.updatePricing(new BigDecimal("79900"), BigDecimal.ZERO);
        product.setStatus(status);
        return product;
    }

    private void saveReturnsArgument() {
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });
    }

    // ---------- Create ----------

    @Test
    void create_normalisesSkuComputesPriceAndDefaultsToActive() {
        when(productRepository.existsBySku("IPHONE-15-128")).thenReturn(false);
        when(categoryRepository.findById(mobiles.getId())).thenReturn(Optional.of(mobiles));
        saveReturnsArgument();

        ProductResponse response = productService.create(request(" iphone-15-128 ", "79900", "10", null), adminId);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getSku()).isEqualTo("IPHONE-15-128");
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(saved.getCreatedBy()).isEqualTo(adminId);
        assertThat(saved.getUpdatedBy()).isEqualTo(adminId);

        assertThat(response.price()).isEqualByComparingTo("79900.00");
        assertThat(response.discountPercent()).isEqualByComparingTo("10.00");
        assertThat(response.sellingPrice()).isEqualByComparingTo("71910.00");
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.category().name()).isEqualTo("Mobiles");
        assertThat(response.imageUrls()).containsExactly("https://cdn.example.com/iphone.jpg");
    }

    @Test
    void create_duplicateSku_throwsConflict() {
        when(productRepository.existsBySku("IPHONE-15")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request("iphone-15", "100", null, null), adminId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("IPHONE-15");
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_unknownCategory_throwsBadRequest() {
        when(productRepository.existsBySku("IPHONE-15")).thenReturn(false);
        when(categoryRepository.findById(mobiles.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(request("IPHONE-15", "100", null, null), adminId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void create_concurrentDuplicateCaughtByConstraint_throwsConflict() {
        when(productRepository.existsBySku("IPHONE-15")).thenReturn(false);
        when(categoryRepository.findById(mobiles.getId())).thenReturn(Optional.of(mobiles));
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenThrow(new DataIntegrityViolationException("uk_products_sku"));

        assertThatThrownBy(() -> productService.create(request("IPHONE-15", "100", null, null), adminId))
                .isInstanceOf(ConflictException.class);
    }

    // ---------- Update ----------

    @Test
    void update_unknownProduct_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(id, request("IPHONE-15", "100", null, null), adminId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_skuTakenByAnotherProduct_throwsConflict() {
        Product product = existingProduct(ProductStatus.ACTIVE);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(productRepository.existsBySkuAndIdNot("PIXEL-8", product.getId())).thenReturn(true);

        assertThatThrownBy(() -> productService.update(product.getId(), request("pixel-8", "100", null, null), adminId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_withoutStatus_keepsCurrentStatusAndRecalculatesPrice() {
        Product product = existingProduct(ProductStatus.INACTIVE);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(categoryRepository.findById(mobiles.getId())).thenReturn(Optional.of(mobiles));
        saveReturnsArgument();

        ProductResponse response = productService.update(product.getId(),
                request("IPHONE-15", "999.00", "15", null), adminId);

        assertThat(response.status()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(response.sellingPrice()).isEqualByComparingTo("849.15");
        assertThat(product.getUpdatedBy()).isEqualTo(adminId);
    }

    // ---------- Delete ----------

    @Test
    void delete_marksProductDiscontinued() {
        Product product = existingProduct(ProductStatus.ACTIVE);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        productService.delete(product.getId(), adminId);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
        assertThat(product.getUpdatedBy()).isEqualTo(adminId);
        verify(productRepository).saveAndFlush(product);
    }

    @Test
    void delete_alreadyDiscontinued_isNoOp() {
        Product product = existingProduct(ProductStatus.DISCONTINUED);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        productService.delete(product.getId(), adminId);

        verify(productRepository, never()).saveAndFlush(any());
    }

    // ---------- Read ----------

    @Test
    void getProduct_inactive_isHiddenFromCustomers() {
        Product product = existingProduct(ProductStatus.INACTIVE);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.getProduct(product.getId(), false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProduct_inactive_isVisibleToAdmins() {
        Product product = existingProduct(ProductStatus.INACTIVE);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        assertThat(productService.getProduct(product.getId(), true).status()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void search_invalidSortField_throwsBadRequest() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(null, null, null, null, null, null);

        assertThatThrownBy(() -> productService.search(criteria, PageRequest.of(0, 20, Sort.by("createdBy")), false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot sort by 'createdBy'");
        verifyNoInteractions(productRepository);
    }

    @Test
    void search_minGreaterThanMax_throwsBadRequest() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(null, null, null,
                new BigDecimal("500"), new BigDecimal("100"), null);

        assertThatThrownBy(() -> productService.search(criteria, PageRequest.of(0, 20), false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_unknownCategory_returnsEmptyPageWithoutQuerying() {
        UUID unknown = UUID.randomUUID();
        when(categoryService.getSelfAndDescendantIds(unknown)).thenReturn(Set.of());
        ProductSearchCriteria criteria = new ProductSearchCriteria(null, unknown, null, null, null, null);

        PageResponse<ProductResponse> page = productService.search(criteria, PageRequest.of(0, 20), false);

        assertThat(page.totalElements()).isZero();
        verify(productRepository, never()).findAll(any(Specification.class), any(PageRequest.class));
    }
}
