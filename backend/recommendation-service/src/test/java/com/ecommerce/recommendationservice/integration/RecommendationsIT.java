package com.ecommerce.recommendationservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <pre>
 * Electronics ─┬─ Phones:      Pixel 9 (Google), Pixel 9 Pro (Google), Galaxy S24 (Samsung), Nokia X (sold out)
 *              ├─ Audio:       Pixel Buds (Google), Galaxy Buds (Samsung)
 *              └─ Accessories: Phone Case (Spigen), Charger (Google)
 * Home:        Coffee Mug
 * Paid orders: A: Pixel 9 + Case + Charger, B: Pixel 9 + Case, C: Pixel 9 + Pixel Buds, D: Galaxy + Galaxy Buds,
 *              E: Pixel 9 + Nokia X
 * </pre>
 */
class RecommendationsIT extends AbstractIntegrationTest {

    private final UUID electronics = UUID.randomUUID();
    private final UUID phones = UUID.randomUUID();
    private final UUID audio = UUID.randomUUID();
    private final UUID accessories = UUID.randomUUID();
    private final UUID home = UUID.randomUUID();
    private final UUID pixel = UUID.randomUUID();
    private final UUID pixelPro = UUID.randomUUID();
    private final UUID galaxy = UUID.randomUUID();
    private final UUID nokia = UUID.randomUUID();
    private final UUID pixelBuds = UUID.randomUUID();
    private final UUID galaxyBuds = UUID.randomUUID();
    private final UUID phoneCase = UUID.randomUUID();
    private final UUID charger = UUID.randomUUID();
    private final UUID mug = UUID.randomUUID();
    private final UUID userA = UUID.randomUUID();
    private final UUID orderB = UUID.randomUUID();

    @BeforeEach
    void shop() {
        indexer.apply(category("CATEGORY_CREATED", electronics, "Electronics", null, 0));
        indexer.apply(category("CATEGORY_CREATED", phones, "Phones", electronics, 0));
        indexer.apply(category("CATEGORY_CREATED", audio, "Audio", electronics, 0));
        indexer.apply(category("CATEGORY_CREATED", accessories, "Accessories", electronics, 0));
        indexer.apply(category("CATEGORY_CREATED", home, "Home", null, 0));
        add(pixel, "Pixel 9", "Google", phones, "71999", 10, "4.5");
        add(pixelPro, "Pixel 9 Pro", "Google", phones, "99999", 10, "4.8");
        add(galaxy, "Galaxy S24", "Samsung", phones, "74999", 10, "4.2");
        add(nokia, "Nokia X", "Nokia", phones, "9999", 0, "3.0");
        add(pixelBuds, "Pixel Buds", "Google", audio, "19999", 10, "4.5");
        add(galaxyBuds, "Galaxy Buds", "Samsung", audio, "14999", 10, "4.1");
        add(phoneCase, "Phone Case", "Spigen", accessories, "999", 10, "3.9");
        add(charger, "Charger", "Google", accessories, "2999", 10, "4.0");
        add(mug, "Coffee Mug", "HomeCo", home, "299", 10, "3.5");
        purchases.apply(paid(UUID.randomUUID(), userA, pixel, phoneCase, charger));
        purchases.apply(paid(orderB, UUID.randomUUID(), pixel, phoneCase));
        purchases.apply(paid(UUID.randomUUID(), UUID.randomUUID(), pixel, pixelBuds));
        purchases.apply(paid(UUID.randomUUID(), UUID.randomUUID(), galaxy, galaxyBuds));
        purchases.apply(paid(UUID.randomUUID(), UUID.randomUUID(), pixel, nokia));
    }

    private void add(UUID id, String name, String brand, UUID categoryId, String price, int stock, String rating) {
        indexer.apply(product(id, name.toUpperCase().replace(' ', '-'), name, brand, categoryId, price, null, name, "ACTIVE", 0,
                Instant.parse("2026-10-01T00:00:00Z")));
        indexer.apply(stock(id, stock, 1));
        indexer.apply(rating(id, rating, 10, 1));
    }

    @Test
    void frequentlyBoughtTogether() throws Exception {
        JsonNode list = data(null, "/products/" + pixel + "/bought-together");
        assertThat(list.get("title").asText()).isEqualTo("Frequently bought together");
        // Case in 2 orders; Buds and Charger in 1 (better rated first); Nokia X is sold out
        assertThat(names(list)).containsExactly("Phone Case", "Pixel Buds", "Charger");
        assertThat(reasons(list).get(0)).isEqualTo("Bought together in 2 orders");
        JsonNode card = list.at("/products/0");
        assertThat(card.get("sellingPrice").decimalValue()).isEqualByComparingTo("999");
        assertThat(card.get("currency").asText()).isEqualTo("INR");
        assertThat(card.get("categoryName").asText()).isEqualTo("Accessories");
        assertThat(card.get("inStock").asBoolean()).isTrue();
        assertThat(names(data(null, "/products/" + pixel + "/bought-together?limit=1"))).containsExactly("Phone Case");
        assertThat(names(data(null, "/products/" + UUID.randomUUID() + "/bought-together"))).isEmpty();
    }

    @Test
    void aCancelledPaidOrderNoLongerCounts() throws Exception {
        purchases.apply(cancelled(orderB, UUID.randomUUID()));
        purchases.apply(cancelled(orderB, UUID.randomUUID())); // repeated: taken back only once
        JsonNode list = data(null, "/products/" + pixel + "/bought-together");
        assertThat(list.at("/products/0/reason").asText()).isEqualTo("Bought together in 1 order");
        assertThat(count("SELECT units FROM daily_sales WHERE product_id = ?", phoneCase)).isEqualTo(1);
    }

    @Test
    void similarProducts() throws Exception {
        JsonNode list = data(null, "/products/" + pixel + "/similar");
        // Same category, similar price, same brand first; then other categories of Electronics; never the Mug or Nokia X
        assertThat(names(list)).containsExactly("Pixel 9 Pro", "Galaxy S24", "Pixel Buds", "Charger", "Galaxy Buds", "Phone Case");
        assertThat(reasons(list).get(0)).isEqualTo("Also in Phones, also by Google");
        assertThat(reasons(list).get(1)).isEqualTo("Also in Phones");
        assertThat(reasons(list).get(2)).isEqualTo("Related: Audio, also by Google");
        getAs(null, URL + "/products/" + UUID.randomUUID() + "/similar").andExpect(status().isNotFound());
    }

    @Test
    void trending() throws Exception {
        JsonNode list = data(null, "/trending?limit=4");
        assertThat(list.get("title").asText()).isEqualTo("Trending now");
        assertThat(names(list)).containsExactly("Pixel 9", "Phone Case", "Pixel Buds", "Galaxy S24");
        assertThat(reasons(list).get(0)).isEqualTo("4 sold in the last 7 days");

        JsonNode inPhones = data(null, "/trending?categoryId=" + phones);
        assertThat(inPhones.get("title").asText()).isEqualTo("Trending in Phones");
        assertThat(names(inPhones)).containsExactly("Pixel 9", "Galaxy S24", "Pixel 9 Pro"); // + top rated, no sales
        assertThat(reasons(inPhones).get(2)).isEqualTo("Top rated");
        assertThat(names(data(null, "/trending?categoryId=" + electronics))).hasSize(7); // with sub-categories; not Nokia X (sold out)

        clock.advance(Duration.ofDays(8)); // sales are older than the window: best rated instead
        assertThat(reasons(data(null, "/trending?limit=3"))).containsOnly("Top rated");
        getAs(null, URL + "/trending?categoryId=" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void recommendedForYou_fromWhatYouBought() throws Exception {
        JsonNode list = data(customerAuth(userA), "/for-you?limit=5");
        assertThat(list.get("title").asText()).isEqualTo("Recommended for you");
        // Bought with Pixel 9 by others: Pixel Buds. Same category as Pixel 9: Galaxy (also popular), Pixel 9 Pro.
        // Never what A already has (Pixel 9, Case, Charger).
        assertThat(names(list)).startsWith("Pixel Buds", "Galaxy S24", "Pixel 9 Pro");
        assertThat(names(list)).doesNotContain("Pixel 9", "Phone Case", "Charger", "Nokia X").hasSize(5);
        assertThat(reasons(list).subList(0, 3)).containsExactly("Because you bought Pixel 9", "Similar to Pixel 9", "Similar to Pixel 9");
        assertThat(reasons(list).get(3)).isEqualTo("Bestseller");
    }

    @Test
    void recommendedForYou_fromWhatYouViewed_andForNewCustomers() throws Exception {
        UUID other = UUID.randomUUID();
        view(customerAuth(other), galaxy).andExpect(status().isNoContent());
        clock.advance(Duration.ofSeconds(1));
        view(customerAuth(other), galaxyBuds).andExpect(status().isNoContent());
        UUID me = UUID.randomUUID();
        view(customerAuth(me), galaxy).andExpect(status().isNoContent());

        JsonNode mine = data(customerAuth(me), "/for-you?limit=3");
        assertThat(names(mine).get(0)).isEqualTo("Galaxy Buds");
        assertThat(reasons(mine).get(0)).isEqualTo("Because you viewed Galaxy S24");
        assertThat(names(mine)).doesNotContain("Galaxy S24");

        JsonNode newcomer = data(customerAuth(UUID.randomUUID()), "/for-you?limit=3");
        assertThat(names(newcomer)).containsExactly("Pixel 9", "Phone Case", "Pixel Buds");
        assertThat(reasons(newcomer)).containsOnly("Bestseller");
    }

    @Test
    void accessAndLimits() throws Exception {
        getAs(null, URL + "/for-you").andExpect(status().isUnauthorized());
        getAs(null, URL + "/trending?limit=0").andExpect(status().isBadRequest());
        getAs(null, URL + "/trending?limit=21").andExpect(status().isBadRequest());
        getAs(null, URL + "/products/not-a-uuid/similar").andExpect(status().isBadRequest());
        getAs(customerAuth(), URL + "/admin/stats").andExpect(status().isForbidden());
        getAs(adminAuth(), URL + "/admin/stats").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products").value(9))
                .andExpect(jsonPath("$.data.recommendable").value(8))
                .andExpect(jsonPath("$.data.paidOrders").value(5))
                .andExpect(jsonPath("$.data.boughtTogetherPairs").value(6)); // P9-Case, P9-Charger, Case-Charger, P9-Buds, S24-Buds, P9-Nokia
    }
}
