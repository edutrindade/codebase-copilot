package com.codebasecopilot.providers;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.providers.ProviderProperties.Provider;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ProvidersControllerTest {

    private static final URI OFFICIAL = URI.create(ProviderProperties.OFFICIAL_BASE_URL);

    @Test
    void onlyOpenAiOutsideLoopbackIsRemote() {
        assertThat(ProvidersController.remote(Provider.OPENAI, OFFICIAL)).isTrue();
        assertThat(ProvidersController.remote(Provider.OPENAI, URI.create("https://llm.example")))
                .isTrue();
        assertThat(
                        ProvidersController.remote(
                                Provider.OPENAI, URI.create("http://localhost:11434/v1")))
                .isFalse();
        assertThat(ProvidersController.remote(Provider.OPENAI, URI.create("http://127.0.0.1/v1")))
                .isFalse();
        assertThat(ProvidersController.remote(Provider.FAKE, OFFICIAL)).isFalse();
        assertThat(ProvidersController.remote(Provider.NONE, OFFICIAL)).isFalse();
    }
}
