package com.ecommerce.searchservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Searching a small catalog:
 * <pre>
 * Electronics ─┬─ Phones: Pixel 9 (Google), Pixel 9 Pro (Google, sold out), Galaxy S24 (Samsung)
 *              ├─ Audio:  Pixel Buds Pro (Google), Noise Cancelling Headphones (Sony)
 *              └─ (itself): USB-C Cable (Anker)
 * Home: Coffee Mug (HomeCo, sold out)
 * Hidden: Pixel 7 (discontinued), Pixel 8 (inactive)
 * </pre>
 */
class SearchIT extends AbstractIntegrationTest {

    private final UUID electronics = UUID.randomUUID();
    private final UUID phones = UUID.randomUUID();
    private final UUID audio = UUID.randomUUID();
    private final UUID home = UUID.randomUUID();
    private final UUID pixel9 = UUID.randomUUID();
    private final UUID pixel9Pro = UUID.randomUUID();
    private final UUID galaxy = UUID.randomUUID();
    private final UUID buds = UUID.randomUUID();
    private final UUID headphones = UUID.randomUUID();
    private final UUID cable = UUID.randomUUID();
    private final UUID mug = UUID.randomUUID();

    @BeforeEach
    void catalog() {
        indexer.apply(category("CATEGORY_CREATED", electronics, "Electronics", null, 0));
        indexer.apply(category("CATEGORY_CREATED", phones, "Phones", electronics, 0));
        indexer.apply(category("CATEGORY_CREATED", audio, "Audio", electronics, 0));
        indexer.apply(category("CATEGORY_CREATED", home, "Home", null, 0));
        Instant day = Instant.parse("2026-10-01T10:00:00Z");
        add(pixel9, "PIXEL-9", "Pixel 9", "Google", phones, "79999", "10", "Google's phone with a great camera", 5, "4.5", 120, day.plusSeconds(86400 * 5));
        add(pixel9Pro, "PIXEL-9-PRO", "Pixel 9 Pro", "Google", phones, "99999", null, "The bigger Pixel", 0, "4.8", 40, day.plusSeconds(86400 * 6));
        add(galaxy, "GALAXY-S24", "Galaxy S24", "Samsung", phones, "74999", null, "Android flagship", 3, "4.2", 300, day.plusSeconds(86400 * 4));
        add(buds, "BUDS-PRO", "Pixel Buds Pro", "Google", audio, "19999", null, "Earbuds", 10, "4.0", 80, day.plusSeconds(86400 * 3));
        add(headphones, "WH-1000", "Noise Cancelling Headphones", "Sony", audio, "24999", null,
                "Wireless, pairs with any phone", 2, "4.7", 500, day.plusSeconds(86400 * 2));
        add(cable, "CABLE-USBC", "USB-C Cable", "Anker", electronics, "499", null, "Charges phones and laptops", 100, "3.5", 20, day.plusSeconds(86400));
        add(mug, "MUG-1", "Coffee Mug", "HomeCo", home, "299", null, "Ceramic", 0, "0", 0, day);
        indexer.apply(product(UUID.randomUUID(), "PIXEL-7", "Pixel 7", "Google", phones, "49999", null, "Old", "DISCONTINUED", 2, day));
        indexer.apply(product(UUID.randomUUID(), "PIXEL-8", "Pixel 8", "Google", phones, "59999", null, "Hidden", "INACTIVE", 1, day));
    }

    private void add(UUID id, String sku, String name, String brand, UUID categoryId, String price, String discount,
                     String description, int available, String average, int reviews, Instant createdAt) {
        indexer.apply(product(id, sku, name, brand, categoryId, price, discount, description, "ACTIVE", 0, createdAt));
        indexer.apply(stock(id, available, 1));
        if (reviews > 0) {
            indexer.apply(rating(id, average, reviews, 1));
        }
    }

    // ---------- Finding ----------

    @Test
    void everyWordMayBeTheStartOfAWord_andHiddenProductsNeverShow() throws Exception {
        assertThat(names(searchData("?q=pixel"))).containsExactlyInAnyOrder("Pixel 9", "Pixel 9 Pro", "Pixel Buds Pro");
        assertThat(names(searchData("?q=pix"))).hasSize(3);
        assertThat(names(searchData("?q=gal%20s2"))).containsExactly("Galaxy S24");
        assertThat(names(searchData("?q=PIXEL%20buds"))).containsExactly("Pixel Buds Pro");
    }

    @Test
    void exactNameFirst_inStockBeforeSoldOut() throws Exception {
        JsonNode data = searchData("?q=pixel%209");
        assertThat(names(data)).containsExactly("Pixel 9", "Pixel 9 Pro"); // Pro is sold out
        JsonNode first = data.at("/results/content/0");
        assertThat(first.get("sellingPrice").decimalValue()).isEqualByComparingTo("71999.10");
        assertThat(first.get("discountPercent").decimalValue()).isEqualByComparingTo("10");
        assertThat(first.get("currency").asText()).isEqualTo("INR");
        assertThat(first.get("inStock").asBoolean()).isTrue();
        assertThat(first.get("availableQuantity").asInt()).isEqualTo(5);
        assertThat(first.get("averageRating").decimalValue()).isEqualByComparingTo("4.5");
        assertThat(first.get("categoryName").asText()).isEqualTo("Phones");
        assertThat(first.get("imageUrl").asText()).isEqualTo("https://cdn.example.com/pixel-9.jpg");
        assertThat(data.get("query").asText()).isEqualTo("pixel 9");
    }

    @Test
    void namesAndCategoriesWeighMoreThanDescriptions_andWordsAreStemmed() throws Exception {
        // "phones": the category of three products, and "phone(s)" in two descriptions
        List<String> found = names(searchData("?q=phones"));
        assertThat(found).containsExactlyInAnyOrder("Pixel 9", "Pixel 9 Pro", "Galaxy S24", "Noise Cancelling Headphones", "USB-C Cable");
        assertThat(found.indexOf("Galaxy S24")).isLessThan(found.indexOf("USB-C Cable"));
        assertThat(found.get(found.size() - 1)).isEqualTo("Pixel 9 Pro"); // sold out goes last
        assertThat(names(searchData("?q=cables"))).containsExactly("USB-C Cable");
        assertThat(names(searchData("?q=anker"))).containsExactly("USB-C Cable"); // brand
        assertThat(names(searchData("?q=s24"))).containsExactly("Galaxy S24");    // SKU part
    }

    // ---------- Filters ----------

    @Test
    void filtersCombine() throws Exception {
        assertThat(names(searchData("?brand=google&brand=SAMSUNG"))).hasSize(4);
        assertThat(names(searchData("?categoryId=" + electronics))).hasSize(6);   // with sub-categories
        assertThat(names(searchData("?categoryId=" + phones))).containsExactlyInAnyOrder("Pixel 9", "Pixel 9 Pro", "Galaxy S24");
        assertThat(names(searchData("?minPrice=1000&maxPrice=25000"))).containsExactlyInAnyOrder("Pixel Buds Pro", "Noise Cancelling Headphones");
        assertThat(names(searchData("?minRating=4"))).hasSize(5);
        assertThat(names(searchData("?inStock=false"))).containsExactlyInAnyOrder("Pixel 9 Pro", "Coffee Mug");
        assertThat(names(searchData("?q=pixel&brand=Google&inStock=true&maxPrice=50000"))).containsExactly("Pixel Buds Pro");
        assertThat(names(searchData("?categoryId=" + UUID.randomUUID()))).isEmpty();
    }

    @Test
    void facetsCountEachFilterWithTheOthersApplied() throws Exception {
        JsonNode facets = searchData("?brand=Google").get("facets");
        // Brands ignore the brand filter, so the other brands are still offered
        List<String> brands = new ArrayList<>();
        facets.get("brands").forEach(b -> brands.add(b.get("value").asText() + "=" + b.get("count").asLong()));
        assertThat(brands).containsExactly("Google=3", "Anker=1", "HomeCo=1", "Samsung=1", "Sony=1");
        // The other facets count Google products only
        List<String> categories = new ArrayList<>();
        facets.get("categories").forEach(c -> categories.add(c.get("name").asText() + "=" + c.get("count").asLong()));
        assertThat(categories).containsExactly("Phones=2", "Audio=1");
        List<Long> prices = new ArrayList<>();
        facets.get("priceRanges").forEach(p -> prices.add(p.get("count").asLong()));
        assertThat(prices).containsExactly(0L, 0L, 0L, 1L, 2L);
        assertThat(facets.at("/priceRanges/0/label").asText()).isEqualTo("Under INR 500");
        assertThat(facets.at("/priceRanges/2/label").asText()).isEqualTo("INR 1,000 - INR 5,000");
        assertThat(facets.at("/priceRanges/4/label").asText()).isEqualTo("INR 20,000 & above");
        assertThat(facets.at("/priceRanges/4/max").isMissingNode()).isTrue();
        assertThat(facets.at("/ratings/0/label").asText()).isEqualTo("4 stars & up");
        assertThat(facets.at("/ratings/0/count").asLong()).isEqualTo(3);
        assertThat(facets.at("/availability/inStock").asLong()).isEqualTo(2);
        assertThat(facets.at("/availability/outOfStock").asLong()).isEqualTo(1);

        assertThat(searchData("?q=pixel&facets=false").has("facets")).isFalse();
    }

    // ---------- Sorting and paging ----------

    @Test
    void sortOptions() throws Exception {
        assertThat(names(searchData("?sort=price_asc&size=3"))).containsExactly("Coffee Mug", "USB-C Cable", "Pixel Buds Pro");
        assertThat(names(searchData("?sort=PRICE_DESC&size=1"))).containsExactly("Pixel 9 Pro");
        assertThat(names(searchData("?sort=rating&size=3"))).containsExactly("Pixel 9 Pro", "Noise Cancelling Headphones", "Pixel 9");
        assertThat(names(searchData("?sort=newest&size=2"))).containsExactly("Pixel 9 Pro", "Pixel 9");
        // Browsing without a query: in stock first, then best rated
        assertThat(names(searchData("?size=2"))).containsExactly("Noise Cancelling Headphones", "Pixel 9");
    }

    @Test
    void paging() throws Exception {
        JsonNode page = searchData("?sort=price_asc&size=3&page=2").get("results");
        assertThat(page.get("totalElements").asLong()).isEqualTo(7);
        assertThat(page.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page.get("last").asBoolean()).isTrue();
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.at("/content/0/name").asText()).isEqualTo("Pixel 9 Pro");
    }

    // ---------- Typos ----------

    @Test
    void misspelledQueriesAreCorrected() throws Exception {
        JsonNode data = searchData("?q=pixle");
        assertThat(data.get("query").asText()).isEqualTo("pixle");
        assertThat(data.get("correctedQuery").asText()).isEqualTo("pixel");
        assertThat(names(data)).hasSize(3);

        assertThat(searchData("?q=galxy%20s24").get("correctedQuery").asText()).isEqualTo("galaxy s24");
        assertThat(searchData("?q=headfones").get("correctedQuery").asText()).isEqualTo("headphones");
        assertThat(searchData("?q=pixel%209").has("correctedQuery")).isFalse();  // found as typed

        JsonNode nothing = searchData("?q=xylophone");
        assertThat(nothing.has("correctedQuery")).isFalse();
        assertThat(nothing.at("/results/totalElements").asLong()).isZero();
        // A typo that would only find something with a filter that excludes it: still nothing, no wrong suggestion
        assertThat(searchData("?q=pixle&brand=Sony").has("correctedQuery")).isFalse();
    }

    // ---------- Suggestions and popular searches ----------

    @Test
    void suggestionsWhileTyping() throws Exception {
        searchData("?q=pixel%209");
        searchData("?q=pixel%209");
        searchData("?q=pixel%20buds");

        JsonNode s = objectMapper.readTree(getAs(null, SEARCH_URL + "/suggest?q=Pixel").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(s.get("query").asText()).isEqualTo("pixel");
        assertThat(s.get("products")).hasSize(3);
        assertThat(s.at("/products/0/sellingPrice").isNumber()).isTrue();
        assertThat(s.get("searches").get(0).asText()).isEqualTo("pixel 9");
        assertThat(s.get("searches")).hasSize(2);

        JsonNode goo = objectMapper.readTree(getAs(null, SEARCH_URL + "/suggest?q=goo").andReturn().getResponse()
                .getContentAsString()).get("data");
        assertThat(goo.get("brands").get(0).asText()).isEqualTo("Google");
        JsonNode aud = objectMapper.readTree(getAs(null, SEARCH_URL + "/suggest?q=aud").andReturn().getResponse()
                .getContentAsString()).get("data");
        assertThat(aud.at("/categories/0/name").asText()).isEqualTo("Audio");
        assertThat(aud.at("/categories/0/id").asText()).isEqualTo(audio.toString());

        JsonNode empty = objectMapper.readTree(getAs(null, SEARCH_URL + "/suggest").andReturn().getResponse()
                .getContentAsString()).get("data");
        assertThat(empty.get("products")).isEmpty();
        assertThat(empty.get("searches").get(0).asText()).isEqualTo("pixel 9");
    }

    @Test
    void popularSearchesCountOnlySearchesThatFoundSomething() throws Exception {
        searchData("?q=Pixel");
        searchData("?q=pixel");
        searchData("?q=pixle");      // counted as what was found: "pixel"
        searchData("?q=anker");
        searchData("?q=xylophone");  // found nothing: not counted
        searchData("?brand=Google"); // no words: not counted

        getAs(null, SEARCH_URL + "/popular?limit=5").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].term").value("pixel"))
                .andExpect(jsonPath("$.data[0].count").value(3))
                .andExpect(jsonPath("$.data[1].term").value("anker"));

        clock.advance(java.time.Duration.ofDays(31)); // older than app.search.popular-days
        getAs(null, SEARCH_URL + "/popular").andExpect(jsonPath("$.data.length()").value(0));
    }

    // ---------- Access and validation ----------

    @Test
    void searchIsPublic_statsAreForAdmins() throws Exception {
        getAs(null, SEARCH_URL + "/products?q=pixel").andExpect(status().isOk());
        getAs(customerAuth(), SEARCH_URL + "/products?q=pixel").andExpect(status().isOk());
        getAs(null, SEARCH_URL + "/admin/stats").andExpect(status().isUnauthorized());
        getAs(customerAuth(), SEARCH_URL + "/admin/stats").andExpect(status().isForbidden());
        getAs(adminAuth(), SEARCH_URL + "/admin/stats").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products").value(9))
                .andExpect(jsonPath("$.data.searchable").value(7))
                .andExpect(jsonPath("$.data.withoutDetails").value(0))
                .andExpect(jsonPath("$.data.categories").value(4));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        search("?minPrice=500&maxPrice=100").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("minPrice")));
        search("?minPrice=-1").andExpect(status().isBadRequest());
        search("?sort=cheapest").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("price_asc")));
        search("?size=51").andExpect(status().isBadRequest());
        search("?page=-1").andExpect(status().isBadRequest());
        search("?minRating=6").andExpect(status().isBadRequest());
        search("?categoryId=phones").andExpect(status().isBadRequest());
        search("?q=" + "a".repeat(101)).andExpect(status().isBadRequest());
        getAs(null, SEARCH_URL + "/popular?limit=0").andExpect(status().isBadRequest());
        // Characters with a meaning in tsquery syntax are just ignored
        assertThat(names(searchData("?q=pixel%27%20%26%20!(9:*"))).contains("Pixel 9");
    }
}
