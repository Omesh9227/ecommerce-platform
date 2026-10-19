package com.ecommerce.productservice.integration;

import com.ecommerce.productservice.dto.request.CategoryRequest;
import com.ecommerce.productservice.dto.request.ProductRequest;
import com.ecommerce.productservice.entity.ProductStatus;
import com.ecommerce.productservice.support.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context + real PostgreSQL (ecommerce_product_test_db) + the real security filter chain.
 * Tokens are signed with the test key, exactly as user-service would sign them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String PRODUCTS_URL = "/api/v1/products";
    protected static final String CATEGORIES_URL = "/api/v1/categories";

    protected final UUID adminId = UUID.randomUUID();
    protected final UUID customerId = UUID.randomUUID();

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE product_images, products, categories");
    }

    protected String adminAuth() {
        return "Bearer " + TestTokens.admin(adminId);
    }

    protected String customerAuth() {
        return "Bearer " + TestTokens.customer(customerId);
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Creates a category through the API as admin and returns its id. */
    protected UUID createCategory(String name, UUID parentId) throws Exception {
        MvcResult result = mockMvc.perform(post(CATEGORIES_URL)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryRequest(name, null, parentId))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).at("/data/id").asText());
    }

    protected ProductRequest productRequest(String sku, String name, String brand, UUID categoryId,
                                            String price, String discount, ProductStatus status) {
        return new ProductRequest(sku, name, "Test description", brand, categoryId,
                new BigDecimal(price), discount == null ? null : new BigDecimal(discount), status,
                List.of("https://cdn.example.com/" + sku.toLowerCase() + ".jpg"));
    }

    /** Creates a product through the API as admin and returns its id. */
    protected UUID createProduct(ProductRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post(PRODUCTS_URL)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).at("/data/id").asText());
    }
}
