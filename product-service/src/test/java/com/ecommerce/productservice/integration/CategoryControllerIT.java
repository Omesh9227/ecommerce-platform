package com.ecommerce.productservice.integration;

import com.ecommerce.productservice.dto.request.CategoryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryControllerIT extends AbstractIntegrationTest {

    // ---------- Read (public) ----------

    @Test
    void tree_isPublicAndNested() throws Exception {
        UUID electronics = createCategory("Electronics", null);
        createCategory("Mobiles", electronics);
        createCategory("Laptops", electronics);
        createCategory("Books", null);

        mockMvc.perform(get(CATEGORIES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Books"))
                .andExpect(jsonPath("$.data[1].name").value("Electronics"))
                .andExpect(jsonPath("$.data[1].children[0].name").value("Laptops"))
                .andExpect(jsonPath("$.data[1].children[1].name").value("Mobiles"));
    }

    @Test
    void getById_showsParentAndChildren() throws Exception {
        UUID electronics = createCategory("Electronics", null);
        UUID mobiles = createCategory("Mobiles", electronics);
        createCategory("Smartphones", mobiles);

        mockMvc.perform(get(CATEGORIES_URL + "/" + mobiles))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Mobiles"))
                .andExpect(jsonPath("$.data.parent.name").value("Electronics"))
                .andExpect(jsonPath("$.data.children[0].name").value("Smartphones"));
    }

    @Test
    void getById_unknown_returns404() throws Exception {
        mockMvc.perform(get(CATEGORIES_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // ---------- Security ----------

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post(CATEGORIES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryRequest("Electronics", null, null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"));
    }

    @Test
    void create_asCustomer_returns403() throws Exception {
        mockMvc.perform(post(CATEGORIES_URL)
                        .header(HttpHeaders.AUTHORIZATION, customerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryRequest("Electronics", null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // ---------- Create ----------

    @Test
    void create_duplicateNameAtSameLevel_isCaseInsensitive409() throws Exception {
        createCategory("Electronics", null);

        mockMvc.perform(post(CATEGORIES_URL)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryRequest("ELECTRONICS", null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void create_sameNameUnderDifferentParents_isAllowed() throws Exception {
        UUID electronics = createCategory("Electronics", null);
        UUID fashion = createCategory("Fashion", null);

        createCategory("Accessories", electronics);
        createCategory("Accessories", fashion);
    }

    @Test
    void create_unknownParent_returns400() throws Exception {
        mockMvc.perform(post(CATEGORIES_URL)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryRequest("Mobiles", null, UUID.randomUUID()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Parent category not found")));
    }

    @Test
    void create_invalidName_returns400() throws Exception {
        mockMvc.perform(post(CATEGORIES_URL)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    // ---------- Update ----------

    @Test
    void update_renameAndMove() throws Exception {
        UUID electronics = createCategory("Electronics", null);
        UUID books = createCategory("Books", null);
        UUID ebooks = createCategory("Ebooks", electronics);

        mockMvc.perform(put(CATEGORIES_URL + "/" + ebooks)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryRequest("E-Books", "Digital books", books))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("E-Books"))
                .andExpect(jsonPath("$.data.parent.name").value("Books"));
    }

    @Test
    void update_moveUnderOwnSubcategory_returns400() throws Exception {
        UUID electronics = createCategory("Electronics", null);
        UUID mobiles = createCategory("Mobiles", electronics);

        mockMvc.perform(put(CATEGORIES_URL + "/" + electronics)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryRequest("Electronics", null, mobiles))))
                .andExpect(status().isBadRequest());
    }

    // ---------- Delete ----------

    @Test
    void delete_withSubcategories_returns409() throws Exception {
        UUID electronics = createCategory("Electronics", null);
        createCategory("Mobiles", electronics);

        mockMvc.perform(delete(CATEGORIES_URL + "/" + electronics).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_withProducts_returns409() throws Exception {
        UUID mobiles = createCategory("Mobiles", null);
        createProduct(productRequest("PIXEL-8", "Pixel 8", "Google", mobiles, "59999", null, null));

        mockMvc.perform(delete(CATEGORIES_URL + "/" + mobiles).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("products")));
    }

    @Test
    void delete_emptyLeaf_returns204ThenGone() throws Exception {
        UUID books = createCategory("Books", null);

        mockMvc.perform(delete(CATEGORIES_URL + "/" + books).header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(CATEGORIES_URL + "/" + books))
                .andExpect(status().isNotFound());
    }
}
