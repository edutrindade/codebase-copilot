package com.codebasecopilot.providers;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Test;

// openai-java-core and openai-java-client-okhttp are released together and must not diverge
// (D-047); both use the openai-java.version property of the pom.
class OpenAiSdkVersionTest {

    @Test
    void coreAndOkHttpClientShareTheVersion() {
        String core = OpenAIClient.class.getPackage().getImplementationVersion();

        assertThat(core).isNotBlank();
        assertThat(OpenAIOkHttpClient.class.getPackage().getImplementationVersion())
                .isEqualTo(core);
    }
}
