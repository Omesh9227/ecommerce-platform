package com.ecommerce.searchservice.controller;

import com.ecommerce.searchservice.dto.response.ApiResult;
import com.ecommerce.searchservice.dto.response.IndexStatsResponse;
import com.ecommerce.searchservice.search.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN: what's in the index. To refill it, call product-service's POST /api/v1/products/events/republish. */
@RestController
@RequestMapping("/api/v1/search/admin")
@RequiredArgsConstructor
@Tag(name = "Search (admin)", description = "Index statistics (ADMIN)")
public class AdminSearchController {

    private final SearchService searchService;

    @Operation(summary = "Index statistics", description = "withoutDetails > 0: products known only from events older "
            + "than Phase 12 - run POST /api/v1/products/events/republish (product-service) to fill them in.")
    @GetMapping("/stats")
    public ResponseEntity<ApiResult<IndexStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResult.of("Index statistics", searchService.stats()));
    }
}
