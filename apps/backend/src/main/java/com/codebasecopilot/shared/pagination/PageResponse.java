package com.codebasecopilot.shared.pagination;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, PageRequestParams params, long totalItems) {
        int totalPages = (int) ((totalItems + params.size() - 1) / params.size());
        return new PageResponse<>(
                List.copyOf(items), params.page(), params.size(), totalItems, totalPages);
    }
}
