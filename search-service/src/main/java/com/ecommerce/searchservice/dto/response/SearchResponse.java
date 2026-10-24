package com.ecommerce.searchservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param query          the words searched for (normalized)
 * @param correctedQuery set when nothing matched the query as typed and a corrected spelling did:
 *                       "Showing results for pixel" (the results are for this one)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResponse(
        @Schema(example = "pixle 9") String query,
        @Schema(example = "pixel 9") String correctedQuery,
        ResultPage<SearchHit> results,
        Facets facets
) {
}
