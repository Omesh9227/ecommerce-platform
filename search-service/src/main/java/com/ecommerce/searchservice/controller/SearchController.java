package com.ecommerce.searchservice.controller;

import com.ecommerce.searchservice.dto.response.ApiResult;
import com.ecommerce.searchservice.dto.response.ErrorResponse;
import com.ecommerce.searchservice.dto.response.PopularSearch;
import com.ecommerce.searchservice.dto.response.SearchResponse;
import com.ecommerce.searchservice.dto.response.SuggestResponse;
import com.ecommerce.searchservice.search.SearchCriteria;
import com.ecommerce.searchservice.search.SearchService;
import com.ecommerce.searchservice.search.SortOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Public product search: no login needed. */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Product search, search-box suggestions and popular searches (public)")
@SecurityRequirements
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "Search products",
            description = "Every word must match name, brand, SKU, category or description, and may be the start of a longer "
                    + "word (\"pix\" finds Pixel). Name and brand matches rank highest. If nothing matches, a corrected "
                    + "spelling is tried (correctedQuery). Without q: browse by the filters. Facets count each filter's options "
                    + "with the other filters applied. Sort: relevance (default), price_asc, price_desc, rating, newest.")
    @ApiResponse(responseCode = "400", description = "Invalid filter, sort or page",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/products")
    public ResponseEntity<ApiResult<SearchResponse>> search(
            @Parameter(example = "pixel 9") @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Also includes its sub-categories") @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @Parameter(description = "Repeat for several: brand=Google&brand=Samsung")
            @RequestParam(name = "brand", required = false) List<String> brands,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @Parameter(description = "1-5: average rating at least this") @RequestParam(name = "minRating", required = false) Integer minRating,
            @RequestParam(name = "inStock", required = false) Boolean inStock,
            @Parameter(example = "relevance") @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @Parameter(description = "false = results only") @RequestParam(name = "facets", defaultValue = "true") boolean facets) {
        SearchCriteria criteria = new SearchCriteria(q, categoryId, brands == null ? List.of() : brands, minPrice, maxPrice,
                minRating, inStock, SortOrder.parse(sort), page, size);
        return ResponseEntity.ok(ApiResult.of("Search results", searchService.search(criteria, facets)));
    }

    @Operation(summary = "Search-box suggestions",
            description = "While typing: matching products, brands, categories and popular searches. Empty q: popular searches.")
    @GetMapping("/suggest")
    public ResponseEntity<ApiResult<SuggestResponse>> suggest(
            @Parameter(example = "pix") @RequestParam(name = "q", required = false) String q) {
        return ResponseEntity.ok(ApiResult.of("Suggestions", searchService.suggest(q)));
    }

    @Operation(summary = "Popular searches", description = "Most searched (and found) recently. Anonymous: only words and counts.")
    @GetMapping("/popular")
    public ResponseEntity<ApiResult<List<PopularSearch>>> popular(
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResult.of("Popular searches", searchService.popular(limit)));
    }
}
