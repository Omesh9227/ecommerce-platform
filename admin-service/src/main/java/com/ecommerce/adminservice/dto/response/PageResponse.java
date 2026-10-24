package com.ecommerce.adminservice.dto.response;

import java.util.List;

/** A page of results (same shape as the other services' pages). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean first,
                              boolean last) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        int totalPages = (int) ((total + size - 1) / size);
        return new PageResponse<>(content, page, size, total, totalPages, page == 0, page >= totalPages - 1);
    }
}
