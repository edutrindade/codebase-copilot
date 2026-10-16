package com.codebasecopilot.providers;

import com.codebasecopilot.providers.ProviderProperties.Provider;
import com.codebasecopilot.providers.StubEmbeddingServer.Reply;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.openai.setup.OpenAiSetup;

// Run in a child JVM by OpenAiLogLevelTest, because OPENAI_LOG is read from the environment.
// "provider" embeds through OpenAiEmbeddingProvider; "raw" uses a client without the log-level
// override, to show the environment variable is active in the child.
final class OpenAiLogProbe {

    static final String CHUNK = "class LedgerService { probeChunkContent(); }";
    static final String DONE = "probe-requests=";

    public static void main(String[] args) throws Exception {
        try (StubEmbeddingServer server = new StubEmbeddingServer()) {
            server.reply(Reply.json(200, StubEmbeddingServer.vectors(4, "", 0)));
            ProviderProperties properties =
                    new ProviderProperties(
                            Provider.OPENAI,
                            "text-embedding-3-small",
                            4,
                            64,
                            0,
                            Duration.ofSeconds(60),
                            50_000,
                            Duration.ofSeconds(10),
                            server.baseUrl(),
                            "sk-probe");
            if (args[0].equals("raw")) {
                embedRaw(properties);
            } else {
                try (OpenAiEmbeddingProvider provider =
                        new OpenAiEmbeddingProvider(properties, Clock.systemUTC())) {
                    provider.embed(List.of(CHUNK));
                }
            }
            System.out.println(DONE + server.requests().size());
        }
    }

    private static void embedRaw(ProviderProperties properties) {
        OpenAIClient client =
                OpenAiSetup.setupSyncClient(
                        properties.baseUrl().toString(),
                        properties.apiKey(),
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        properties.model(),
                        properties.timeout(),
                        0,
                        null,
                        null,
                        ObservationRegistry.NOOP,
                        null,
                        List.of());
        try {
            client.embeddings()
                    .create(
                            EmbeddingCreateParams.builder()
                                    .model(properties.model())
                                    .inputOfArrayOfStrings(List.of(CHUNK))
                                    .build());
        } finally {
            client.close();
        }
    }

    private OpenAiLogProbe() {}
}
