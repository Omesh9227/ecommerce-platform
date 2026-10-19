package com.ecommerce.productservice.integration;

import com.ecommerce.productservice.dto.request.ProductRequest;
import com.ecommerce.productservice.entity.ProductStatus;
import com.ecommerce.productservice.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerIT extends AbstractIntegrationTest {

    // ---------- Create ----------

    @Test
    void create_asAdmin_returns201WithCalculatedSellingPrice() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);

        mockMvc.perform(post(PRODUCTS_URL)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productRequest("iphone-15-128", "Apple iPhone 15", "Apple", mobiles,
                                "79900", "10", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product created successfully"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.sku").value("IPHONE-15-128"))
                .andExpect(jsonPath("$.data.price").value(79900.00))
                .andExpect(jsonPath("$.data.discountPercent").value(10.00))
                .andExpect(jsonPath("$.data.sellingPrice").value(71910.00))
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.category.name").value("Mobiles"))
                .andExpect(jsonPath("$.data.imageUrls[0]").value("https://cdn.example.com/iphone-15-128.jpg"));
    }

    @Test
    void create_invalidFields_returns400WithFieldErrors() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        String body = """
                {"sku":"x!","name":"","categoryId":"%s","price":-5,"discountPercent":100,
                 "imageUrls":["ftp://not-allowed"]}
                """.formatted(mobiles);

        mockMvc.perform(post(PRODUCTS_URL)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.sku").exists())
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.price").exists())
                .andExpect(jsonPath("$.validationErrors.discountPercent").exists())
                .andExpect(jsonPath("$.validationErrors['imageUrls[0]']").exists());
    }

    @Test
    void create_duplicateSku_isCaseInsensitive409() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", null, null));

        mockMvc.perform(post(PRODUCTS_URL)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productRequest("pixel-8", "Another", "Google", mobiles, "100", null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A product with SKU 'PIXEL-8' already exists"));
    }

    @Test
    void create_unknownCategory_returns400() throws Exception {
        mockMvc.perform(post(PRODUCTS_URL)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productRequest("PIXEL-8", "Pixel 8", "Google", UUID.randomUUID(),
                                "59999", null, null))))
                .andExpect(status().isBadRequest());
    }

    // ---------- Security ----------

    @Test
    void create_asCustomer_returns403() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);

        mockMvc.perform(post(PRODUCTS_URL)
                        .header(HttpHeaders.AUTHORIZATION, customerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post(PRODUCTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withExpiredToken_returns401WithReason() throws Exception {
        mockMvc.perform(post(PRODUCTS_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.expiredAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token has expired"));
    }

    @Test
    void publicGet_withInvalidToken_stillWorksAsAnonymous() throws Exception {
        mockMvc.perform(get(PRODUCTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer garbage.token.value"))
                .andExpect(status().isOk());
    }

    // ---------- Visibility ----------

    @Test
    void list_customersSeeOnlyActive_adminsSeeAllAndCanFilterByStatus() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        createProduct(productRequest("ACTIVE-1", "Visible phone", "Google", mobiles, "100", null, null));
        createProduct(productRequest("HIDDEN-1", "Hidden phone", "Google", mobiles, "100", null, ProductStatus.INACTIVE));

        mockMvc.perform(get(PRODUCTS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("ACTIVE-1"));

        // A customer asking for INACTIVE still only gets ACTIVE
        mockMvc.perform(get(PRODUCTS_URL).param("status", "INACTIVE").header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("ACTIVE-1"));

        mockMvc.perform(get(PRODUCTS_URL).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get(PRODUCTS_URL).param("status", "INACTIVE").header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("HIDDEN-1"));
    }

    @Test
    void getById_inactive_is404ForPublicButVisibleToAdmin() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        UUID hidden = createProduct(productRequest("HIDDEN-1", "Hidden", "Google", mobiles, "100", null,
                ProductStatus.INACTIVE));

        mockMvc.perform(get(PRODUCTS_URL + "/" + hidden))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(PRODUCTS_URL + "/" + hidden).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void getById_malformedId_returns400() throws Exception {
        mockMvc.perform(get(PRODUCTS_URL + "/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Filters, search, sort, paging ----------

    @Test
    void filterByParentCategory_includesSubcategories() throws Exception {
        UUID electronics = createCategory("Electronics", null);
        UUID mobiles = createCategory("Mobiles", electronics);
        UUID laptops = createCategory("Laptops", electronics);
        UUID books = createCategory("Books", null);
        createProduct(productRequest("PHONE-1", "Phone", "Google", mobiles, "100", null, null));
        createProduct(productRequest("LAPTOP-1", "Laptop", "Dell", laptops, "200", null, null));
        createProduct(productRequest("BOOK-1", "Book", "Penguin", books, "10", null, null));

        mockMvc.perform(get(PRODUCTS_URL).param("categoryId", electronics.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].sku", containsInAnyOrder("PHONE-1", "LAPTOP-1")));

        mockMvc.perform(get(PRODUCTS_URL).param("categoryId", mobiles.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get(PRODUCTS_URL).param("categoryId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void filterByPrice_usesSellingPrice() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        // 1000 with 50% off sells at 500; 700 with no discount sells at 700
        createProduct(productRequest("DISCOUNTED", "Discounted", "A", mobiles, "1000", "50", null));
        createProduct(productRequest("FULL-PRICE", "Full price", "B", mobiles, "700", null, null));

        mockMvc.perform(get(PRODUCTS_URL).param("maxPrice", "600"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("DISCOUNTED"));

        mockMvc.perform(get(PRODUCTS_URL).param("minPrice", "600").param("maxPrice", "800"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("FULL-PRICE"));
    }

    @Test
    void searchAndBrand_areCaseInsensitive() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        createProduct(productRequest("IPHONE-15", "Apple iPhone 15", "Apple", mobiles, "79900", null, null));
        createProduct(productRequest("PIXEL-8", "Google Pixel 8", "Google", mobiles, "59999", null, null));

        mockMvc.perform(get(PRODUCTS_URL).param("search", "IPHONE"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("IPHONE-15"));

        mockMvc.perform(get(PRODUCTS_URL).param("brand", "google"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("PIXEL-8"));

        // "%" is searched literally, not as a wildcard
        mockMvc.perform(get(PRODUCTS_URL).param("search", "%"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void sortAndPagination() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        createProduct(productRequest("P-300", "Phone 300", "A", mobiles, "300", null, null));
        createProduct(productRequest("P-100", "Phone 100", "A", mobiles, "100", null, null));
        createProduct(productRequest("P-200", "Phone 200", "A", mobiles, "200", null, null));

        mockMvc.perform(get(PRODUCTS_URL).param("sort", "sellingPrice,asc").param("size", "2").param("page", "0"))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].sku").value("P-100"))
                .andExpect(jsonPath("$.data.content[1].sku").value("P-200"))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false));
    }

    @Test
    void invalidSortOrPriceRange_returns400() throws Exception {
        mockMvc.perform(get(PRODUCTS_URL).param("sort", "createdBy,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Cannot sort by 'createdBy'")));

        mockMvc.perform(get(PRODUCTS_URL).param("minPrice", "500").param("maxPrice", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("minPrice cannot be greater than maxPrice"));
    }

    // ---------- Update / delete ----------

    @Test
    void update_replacesFieldsAndRecalculatesPrice() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        UUID tablets = createCategory("Tablets", null);
        UUID id = createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", null, null));

        ProductRequest update = new ProductRequest("PIXEL-TAB", "Pixel Tablet", "Wi-Fi", "Google", tablets,
                new BigDecimal("999.00"), new BigDecimal("15"), ProductStatus.INACTIVE,
                List.of("https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg"));

        mockMvc.perform(put(PRODUCTS_URL + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("PIXEL-TAB"))
                .andExpect(jsonPath("$.data.name").value("Pixel Tablet"))
                .andExpect(jsonPath("$.data.category.name").value("Tablets"))
                .andExpect(jsonPath("$.data.sellingPrice").value(849.15))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.imageUrls", hasSize(2)))
                .andExpect(jsonPath("$.data.imageUrls[1]").value("https://cdn.example.com/b.jpg"));
    }

    @Test
    void delete_isSoft_hiddenFromPublic_visibleToAdminAsDiscontinued() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        UUID id = createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", null, null));

        mockMvc.perform(delete(PRODUCTS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isNoContent());

        // Deleting again is harmless
        mockMvc.perform(delete(PRODUCTS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PRODUCTS_URL + "/" + id))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(PRODUCTS_URL + "/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISCONTINUED"));
    }

    @Test
    void delete_unknownProduct_returns404() throws Exception {
        mockMvc.perform(delete(PRODUCTS_URL + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isNotFound());
    }
}
