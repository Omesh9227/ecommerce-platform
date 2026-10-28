package com.ecommerce.productservice.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/products/lookup - used by cart-service to refresh several products in one call. */
class ProductLookupIT extends AbstractIntegrationTest {

    private static final String LOOKUP_URL = PRODUCTS_URL + "/lookup";

    @Test
    void lookup_isPublic_andReturnsOnlyActiveProducts() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        UUID pixel = createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", "10", null));
        UUID iphone = createProduct(productRequest("IPHONE-15", "iPhone 15", "Apple", mobiles, "79900", null, null));
        UUID discontinued = createProduct(productRequest("OLD-1", "Old phone", "Acme", mobiles, "999", null, null));
        mockMvc.perform(delete(PRODUCTS_URL + "/" + discontinued).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isNoContent());
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(get(LOOKUP_URL).param("ids", pixel + "," + iphone + "," + discontinued + "," + unknown))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].sku").value(containsInAnyOrder("PIXEL-8", "IPHONE-15")))
                .andExpect(jsonPath("$.data[?(@.sku == 'PIXEL-8')].sellingPrice").value(53999.10))
                .andExpect(jsonPath("$.data[?(@.sku == 'PIXEL-8')].inStock").value(false));
    }

    @Test
    void lookup_validation() throws Exception {
        mockMvc.perform(get(LOOKUP_URL)).andExpect(status().isBadRequest());
        mockMvc.perform(get(LOOKUP_URL).param("ids", "not-a-uuid")).andExpect(status().isBadRequest());

        String tooMany = Stream.generate(UUID::randomUUID).limit(51).map(UUID::toString).collect(Collectors.joining(","));
        mockMvc.perform(get(LOOKUP_URL).param("ids", tooMany))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("At most 50 product ids per lookup"));
    }
}
