package com.codebasecopilot.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PageResponseTest {

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "20, 1", "21, 2", "40, 2", "41, 3"})
    void computesTotalPages(long totalItems, int expectedPages) {
        PageResponse<String> response =
                PageResponse.of(List.of(), new PageRequestParams(0, 20), totalItems);

        assertThat(response.totalPages()).isEqualTo(expectedPages);
        assertThat(response.totalItems()).isEqualTo(totalItems);
    }

    @Test
    void keepsRequestedPageBeyondTheLast() {
        PageResponse<String> response = PageResponse.of(List.of(), new PageRequestParams(7, 5), 3);

        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isEqualTo(7);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    void mapsItemsKeepingPagination() {
        PageResponse<Integer> response =
                PageResponse.of(List.of("a", "bb"), new PageRequestParams(1, 2), 4)
                        .map(String::length);

        assertThat(response).isEqualTo(new PageResponse<>(List.of(1, 2), 1, 2, 4, 2));
    }
}
