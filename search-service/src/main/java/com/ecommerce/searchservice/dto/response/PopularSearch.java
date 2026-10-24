package com.ecommerce.searchservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** A frequent search (anonymous: only the words and how often). */
public record PopularSearch(@Schema(example = "pixel 9") String term, @Schema(example = "42") long count) {
}
