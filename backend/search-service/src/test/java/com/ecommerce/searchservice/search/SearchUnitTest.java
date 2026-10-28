package com.ecommerce.searchservice.search;

import com.ecommerce.searchservice.config.SearchProperties;
import com.ecommerce.searchservice.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchUnitTest {

    @Test
    void wordsAreLowerCaseLettersAndDigitsOnly() {
        assertThat(QueryText.words("Pixel 8 Pro (128GB) – Obsidian!")).containsExactly("pixel", "8", "pro", "128gb", "obsidian");
        assertThat(QueryText.words("  ")).isEmpty();
        assertThat(QueryText.words(null)).isEmpty();
        // Anything that could break a tsquery (quotes, &, |, !, :) never reaches it
        assertThat(QueryText.tsQuery(QueryText.queryWords("pixel' & !(drop:*"))).isEqualTo("pixel:* & drop:*");
    }

    @Test
    void queryWordsAreUniqueAndLimited() {
        assertThat(QueryText.queryWords("pixel PIXEL pixel 9")).containsExactly("pixel", "9");
        assertThat(QueryText.queryWords("a b c d e f g h i j k")).hasSize(8);
        assertThat(QueryText.tsQuery(List.of())).isNull();
        assertThat(QueryText.normalized(QueryText.queryWords("  Pixel   9 "))).isEqualTo("pixel 9");
    }

    @Test
    void dictionaryWordsSkipSingleCharacters() {
        assertThat(QueryText.dictionaryWords("Pixel 9 Pro", "Google", null)).containsExactly("pixel", "pro", "google");
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "pixle, pixel, 1",        // two neighbouring letters swapped
            "pixel, pixel, 0",
            "pixl, pixel, 1",         // one missing
            "pixxel, pixel, 1",       // one extra
            "headfones, headphones, 2",
            "galxy, galaxy, 1",
            "phone, phones, 1",
            "abc, xyz, 3"
    })
    void editDistance(String typed, String word, int distance) {
        assertThat(SpellingCorrector.editDistance(typed, word)).isEqualTo(distance);
    }

    @Test
    void sortNamesAreCaseInsensitive_andUnknownOnesAreRejected() {
        assertThat(SortOrder.parse("price_ASC")).isEqualTo(SortOrder.PRICE_ASC);
        assertThat(SortOrder.parse(null)).isEqualTo(SortOrder.RELEVANCE);
        assertThatThrownBy(() -> SortOrder.parse("cheapest")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("relevance, price_asc, price_desc, rating, newest");
    }

    @Test
    void priceRangesMustBeAscending() {
        assertThatThrownBy(() -> new SearchProperties("INR", List.of(new BigDecimal("1000"), new BigDecimal("500")), 50, 8, 30, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchProperties("INR", List.of(BigDecimal.ZERO), 50, 8, 30, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
