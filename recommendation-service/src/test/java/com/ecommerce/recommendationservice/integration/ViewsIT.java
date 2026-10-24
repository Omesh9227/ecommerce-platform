package com.ecommerce.recommendationservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Product views: recently viewed, "customers who viewed this also viewed", and clearing the history. */
class ViewsIT extends AbstractIntegrationTest {

    private final UUID phones = UUID.randomUUID();
    private final List<UUID> products = new ArrayList<>();

    @BeforeEach
    void catalog() {
        indexer.apply(category("CATEGORY_CREATED", phones, "Phones", null, 0));
        for (int i = 0; i < 6; i++) {
            UUID id = UUID.randomUUID();
            products.add(id);
            indexer.apply(product(id, "P-" + i, "Phone " + i, "Brand", phones, "1000", null, "Phone", "ACTIVE", 0, Instant.now()));
            indexer.apply(stock(id, 5, 1));
        }
    }

    @Test
    void recentlyViewed_newestFirst_andClearable() throws Exception {
        UUID me = UUID.randomUUID();
        String auth = customerAuth(me);
        for (int i = 0; i < 3; i++) {
            view(auth, products.get(i)).andExpect(status().isNoContent());
            clock.advance(Duration.ofSeconds(1));
        }
        view(auth, products.get(0)).andExpect(status().isNoContent()); // seen again: moves to the front

        JsonNode recent = data(auth, "/recently-viewed");
        assertThat(recent.get("title").asText()).isEqualTo("Recently viewed");
        assertThat(names(recent)).containsExactly("Phone 0", "Phone 2", "Phone 1");
        assertThat(names(data(customerAuth(UUID.randomUUID()), "/recently-viewed"))).isEmpty(); // only your own

        mockMvc.perform(delete(java.net.URI.create(URL + "/recently-viewed")).header(org.springframework.http.HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNoContent());
        assertThat(names(data(auth, "/recently-viewed"))).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM viewed_together")).isEqualTo(6); // anonymous counts stay
    }

    @Test
    void alsoViewed_countsCustomersNotReloads() throws Exception {
        String x = customerAuth(UUID.randomUUID());
        view(x, products.get(0));
        view(x, products.get(1));
        String y = customerAuth(UUID.randomUUID());
        view(y, products.get(0));
        view(y, products.get(1));
        view(y, products.get(2));
        String z = customerAuth(UUID.randomUUID());
        view(z, products.get(0));
        for (int i = 0; i < 5; i++) {
            view(z, products.get(2)); // reloading the page: counted once
        }

        JsonNode list = data(null, "/products/" + products.get(0) + "/also-viewed");
        assertThat(list.get("title").asText()).isEqualTo("Customers who viewed this also viewed");
        assertThat(names(list)).containsExactlyInAnyOrder("Phone 1", "Phone 2");
        assertThat(reasons(list)).containsOnly("Viewed by 2 customers who viewed this");
    }

    @Test
    void viewsNeedALoginAndAKnownProduct() throws Exception {
        view(null, products.get(0)).andExpect(status().isUnauthorized());
        view(customerAuth(), UUID.randomUUID()).andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(java.net.URI.create(URL + "/views"))
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, customerAuth())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sixPagesOpenedAtOnceAreAllViewedTogether() throws Exception {
        String auth = customerAuth(UUID.randomUUID());
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> results = new ArrayList<>();
        for (UUID id : products) {
            results.add(pool.submit(() -> view(auth, id).andReturn().getResponse().getStatus()));
        }
        for (Future<Integer> f : results) {
            assertThat(f.get()).isEqualTo(204);
        }
        pool.shutdown();
        // 6 products: 15 pairs, stored both ways, each seen by this one customer
        assertThat(count("SELECT COUNT(*) FROM viewed_together WHERE customers = 1")).isEqualTo(30);
    }
}
