package com.codebasecopilot.providers;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
@TestPropertySource(properties = {"app.embeddings.provider=none", "app.generation.provider=none"})
class ProvidersDisabledApiIT {

    @Autowired private MockMvcTester mvc;

    @Test
    void reportsNoModelWithoutProviders() {
        assertThat(mvc.get().uri("/api/v1/system/providers"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo(
                        """
                        {
                          "embeddings": {"provider": "none", "model": null, "dimensions": null,
                                         "remote": false},
                          "generation": {"provider": "none", "model": null, "remote": false}
                        }
                        """);
    }
}
