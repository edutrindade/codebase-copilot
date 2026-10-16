package com.codebasecopilot.shared.pagination;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, PageRequestParams params, long totalItems) {
        int totalPages = (int) ((totalItems + params.size() - 1) / params.size());
        return new PageResponse<>(
                List.copyOf(items), params.page(), params.size(), totalItems, totalPages);
    }

    public <R> PageResponse<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResponse<>(
                items.stream().<R>map(mapper).toList(), page, size, totalItems, totalPages);
    }
}
