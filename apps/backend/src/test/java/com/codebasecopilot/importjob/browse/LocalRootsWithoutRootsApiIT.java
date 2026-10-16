package com.codebasecopilot.importjob.browse;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.IntegrationTestWithoutLocalRoots;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTestWithoutLocalRoots
class LocalRootsWithoutRootsApiIT {

    @Autowired private MockMvcTester mvc;

    @Test
    void listsNoRoots() {
        assertThat(mvc.get().uri("/api/v1/local-roots"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("[]");
    }

    @Test
    void directoriesOfAnyRootAreNotFound() {
        assertThat(mvc.get().uri("/api/v1/local-roots/default/directories"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("ROOT_NOT_FOUND");
    }
}
