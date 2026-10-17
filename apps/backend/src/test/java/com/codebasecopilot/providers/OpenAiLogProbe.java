package com.codebasecopilot.providers;

import com.codebasecopilot.providers.GenerationRequest.OutputSchema;
import com.codebasecopilot.providers.ProviderProperties.Provider;
import com.codebasecopilot.providers.StubOpenAiServer.Reply;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

// Run in a child JVM by OpenAiLogLevelTest, because OPENAI_LOG is read from the environment.
// "provider" embeds through OpenAiEmbeddingProvider and "generation" generates through
// OpenAiGenerationProvider; "raw" uses a client without the log-level override, to show the
// environment variable is active in the child.
final class OpenAiLogProbe {

    static final String CHUNK = "class LedgerService { probeChunkContent(); }";
    static final String DONE = "probe-requests=";

    public static void main(String[] args) throws Exception {
        try (StubOpenAiServer server = new StubOpenAiServer()) {
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
            switch (args[0]) {
                case "raw" -> {
                    server.reply(Reply.json(200, StubOpenAiServer.vectors(4, "", 0)));
                    embedRaw(properties);
                }
                case "generation" -> {
                    server.reply(
                            Reply.json(
                                    200,
                                    StubOpenAiServer.completion(
                                            "stop", "{\"answer\":\"" + CHUNK + "\"}", null, "")));
                    generate(server);
                }
                default -> {
                    server.reply(Reply.json(200, StubOpenAiServer.vectors(4, "", 0)));
                    try (OpenAiEmbeddingProvider provider =
                            new OpenAiEmbeddingProvider(properties, Clock.systemUTC())) {
                        provider.embed(List.of(CHUNK));
                    }
                }
            }
            System.out.println(DONE + server.requests().size());
        }
    }

    private static void generate(StubOpenAiServer server) {
        GenerationProperties properties =
                new GenerationProperties(
                        Provider.OPENAI,
                        "gpt-6-luna",
                        "low",
                        2000,
                        Duration.ofSeconds(10),
                        server.baseUrl(),
                        "sk-probe");
        try (OpenAiGenerationProvider provider = new OpenAiGenerationProvider(properties)) {
            provider.generate(
                    new GenerationRequest(
                            "Answer from the evidence.",
                            CHUNK,
                            new OutputSchema("answer", Map.of("type", "object"))));
        }
    }

    private static void embedRaw(ProviderProperties properties) {
        OpenAIClient client =
                OpenAIOkHttpClient.builder()
                        .baseUrl(properties.baseUrl().toString())
                        .apiKey("sk-probe")
                        .maxRetries(0)
                        .build();
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
