package com.ecommerce.searchservice.dto.response;

import java.util.List;

/** A page of results (same shape as the other services' pages). */
public record ResultPage<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> ResultPage<T> of(List<T> content, int page, int size, long total) {
        int totalPages = size == 0 ? 0 : (int) ((total + size - 1) / size);
        return new ResultPage<>(content, page, size, total, totalPages, page == 0, page >= totalPages - 1);
    }
}
