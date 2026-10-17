package com.codebasecopilot.providers;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class ProvidersApiIT {

    @Autowired private MockMvcTester mvc;

    @Test
    void describesTheConfiguredProviders() {
        assertThat(mvc.get().uri("/api/v1/system/providers"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo(
                        """
                        {
                          "embeddings": {"provider": "fake", "model": "text-embedding-3-small",
                                         "dimensions": 1536, "remote": false},
                          "generation": {"provider": "fake", "model": "gpt-6-luna",
                                         "remote": false}
                        }
                        """);
    }
}
