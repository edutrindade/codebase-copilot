package com.codebasecopilot.shared.pagination;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageRequestParams(
        @Schema(defaultValue = "0") @Min(value = 0, message = "{validation.min}") Integer page,
        @Schema(defaultValue = "20")
                @Min(value = 1, message = "{validation.min}")
                @Max(value = PageRequestParams.MAX_SIZE, message = "{validation.max}")
                Integer size) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequestParams {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    public long offset() {
        return (long) page * size;
    }
}
