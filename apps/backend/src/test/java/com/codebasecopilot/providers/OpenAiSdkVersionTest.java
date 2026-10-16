package com.codebasecopilot.providers;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.OpenAIClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

// openai-java-core is declared explicitly but not managed by spring-ai-bom (D-047).
class OpenAiSdkVersionTest {

    private static final Pattern SDK_DEPENDENCY =
            Pattern.compile(
                    "<artifactId>openai-java-core</artifactId>\\s*<version>([^<]+)</version>");

    @Test
    void sdkMatchesTheVersionSpringAiDeclares() throws IOException {
        String springAiPom;
        try (InputStream in =
                getClass()
                        .getResourceAsStream(
                                "/META-INF/maven/org.springframework.ai/spring-ai-openai/pom.xml")) {
            assertThat(in).isNotNull();
            springAiPom = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher declared = SDK_DEPENDENCY.matcher(springAiPom);

        assertThat(declared.find()).isTrue();
        assertThat(OpenAIClient.class.getPackage().getImplementationVersion())
                .isEqualTo(declared.group(1));
    }
}
