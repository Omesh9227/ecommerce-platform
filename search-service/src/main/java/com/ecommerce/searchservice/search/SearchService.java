package com.ecommerce.searchservice.search;

import com.ecommerce.searchservice.config.SearchProperties;
import com.ecommerce.searchservice.dto.response.IndexStatsResponse;
import com.ecommerce.searchservice.dto.response.PopularSearch;
import com.ecommerce.searchservice.dto.response.ResultPage;
import com.ecommerce.searchservice.dto.response.SearchHit;
import com.ecommerce.searchservice.dto.response.SearchResponse;
import com.ecommerce.searchservice.dto.response.SuggestResponse;
import com.ecommerce.searchservice.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Search, suggestions and popular searches.
 * A search with a query that finds nothing is tried once more with corrected spelling; the response then says
 * which query the results are for. Searches that found something are counted (anonymously) for popular searches.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_POPULAR = 20;
    private static final int SUGGESTED_BRANDS = 5;
    private static final int SUGGESTED_CATEGORIES = 5;
    private static final int SUGGESTED_SEARCHES = 5;

    private final SearchRepository repository;
    private final SpellingCorrector spellingCorrector;
    private final SearchProperties properties;
    private final Clock clock;

    public SearchResponse search(SearchCriteria criteria, boolean withFacets) {
        validate(criteria);
        List<String> words = QueryText.queryWords(criteria.query());
        long total = repository.count(criteria, words);
        String corrected = null;
        if (total == 0 && !words.isEmpty()) {
            Optional<List<String>> fixed = spellingCorrector.correct(words);
            if (fixed.isPresent()) {
                long fixedTotal = repository.count(criteria, fixed.get());
                if (fixedTotal > 0) {
                    words = fixed.get();
                    total = fixedTotal;
                    corrected = QueryText.normalized(words);
                }
            }
        }
        List<SearchHit> hits = total == 0 ? List.of() : repository.page(criteria, words);
        if (total > 0 && !words.isEmpty()) {
            record(QueryText.normalized(words));
        }
        return new SearchResponse(words.isEmpty() ? null : QueryText.normalized(QueryText.queryWords(criteria.query())),
                corrected, ResultPage.of(hits, criteria.page(), criteria.size(), total),
                withFacets ? repository.facets(criteria, words) : null);
    }

    public SuggestResponse suggest(String query) {
        checkLength(query);
        List<String> words = QueryText.queryWords(query);
        if (words.isEmpty()) {
            List<String> popular = repository.popular(since(), SUGGESTED_SEARCHES).stream().map(PopularSearch::term).toList();
            return new SuggestResponse("", List.of(), List.of(), List.of(), popular);
        }
        String typed = QueryText.normalized(words);
        String lastWord = words.get(words.size() - 1);
        return new SuggestResponse(typed,
                repository.suggestProducts(words, properties.suggestionLimit()),
                repository.suggestBrands(typed, SUGGESTED_BRANDS),
                repository.suggestCategories(lastWord, SUGGESTED_CATEGORIES),
                repository.suggestSearches(typed, since(), SUGGESTED_SEARCHES));
    }

    public List<PopularSearch> popular(int limit) {
        if (limit < 1 || limit > MAX_POPULAR) {
            throw new BadRequestException("limit must be between 1 and " + MAX_POPULAR);
        }
        return repository.popular(since(), limit);
    }

    public IndexStatsResponse stats() {
        return repository.stats();
    }

    // ---------- Helpers ----------

    private void validate(SearchCriteria c) {
        checkLength(c.query());
        if (c.page() < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (c.size() < 1 || c.size() > properties.maxPageSize()) {
            throw new BadRequestException("size must be between 1 and " + properties.maxPageSize());
        }
        if ((c.minPrice() != null && c.minPrice().signum() < 0) || (c.maxPrice() != null && c.maxPrice().signum() < 0)) {
            throw new BadRequestException("Prices can't be negative");
        }
        if (c.minPrice() != null && c.maxPrice() != null && c.minPrice().compareTo(c.maxPrice()) > 0) {
            throw new BadRequestException("minPrice can't be more than maxPrice");
        }
        if (c.minRating() != null && (c.minRating() < 1 || c.minRating() > 5)) {
            throw new BadRequestException("minRating must be between 1 and 5");
        }
        if (c.brands() != null && c.brands().size() > 20) {
            throw new BadRequestException("At most 20 brands");
        }
    }

    private static void checkLength(String query) {
        if (query != null && query.length() > QueryText.MAX_QUERY_LENGTH) {
            throw new BadRequestException("q must be at most " + QueryText.MAX_QUERY_LENGTH + " characters");
        }
    }

    /** Counting a search must never make the search itself fail. */
    private void record(String term) {
        try {
            repository.recordSearch(term, clock.instant());
        } catch (DataAccessException ex) {
            log.warn("Could not count search '{}': {}", term, ex.getMessage());
        }
    }

    private Instant since() {
        return clock.instant().minus(Duration.ofDays(properties.popularDays()));
    }
}
