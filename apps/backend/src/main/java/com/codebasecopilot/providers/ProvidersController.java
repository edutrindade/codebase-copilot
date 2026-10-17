package com.codebasecopilot.providers;

import com.codebasecopilot.providers.ProviderProperties.Provider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// What the UI tells the user before sending code to a provider (03 §7). Never the key, the
// base URL or the prices.
@RestController
@RequestMapping("/api/v1/system/providers")
@Tag(
        name = "Sistema",
        description = "Provedores configurados no backend e para onde vão os trechos de código.")
class ProvidersController {

    private final ProviderProperties embeddingProperties;
    private final GenerationProperties generationProperties;
    private final @Nullable EmbeddingProvider embeddings;
    private final @Nullable GenerationProvider generation;

    ProvidersController(
            ProviderProperties embeddingProperties,
            GenerationProperties generationProperties,
            ObjectProvider<EmbeddingProvider> embeddings,
            ObjectProvider<GenerationProvider> generation) {
        this.embeddingProperties = embeddingProperties;
        this.generationProperties = generationProperties;
        this.embeddings = embeddings.getIfAvailable();
        this.generation = generation.getIfAvailable();
    }

    @GetMapping
    @Operation(
            operationId = "getProviders",
            summary = "Mostra os provedores de embeddings e de respostas configurados")
    ProvidersResponse get() {
        ModelInfo embeddingModel = embeddings == null ? null : embeddings.model();
        GenerationModel generationModel = generation == null ? null : generation.model();
        return new ProvidersResponse(
                new EmbeddingProviderResponse(
                        name(embeddingProperties.provider()),
                        embeddingModel == null ? null : embeddingModel.model(),
                        embeddingModel == null ? null : embeddingModel.dimensions(),
                        remote(embeddingProperties.provider(), embeddingProperties.baseUrl())),
                new GenerationProviderResponse(
                        name(generationProperties.provider()),
                        generationModel == null ? null : generationModel.model(),
                        remote(generationProperties.provider(), generationProperties.baseUrl())));
    }

    static boolean remote(Provider provider, URI baseUrl) {
        String host = baseUrl.getHost() == null ? "" : baseUrl.getHost().toLowerCase(Locale.ROOT);
        return provider == Provider.OPENAI && !OpenAiClients.LOOPBACK_HOSTS.contains(host);
    }

    private static String name(Provider provider) {
        return provider.name().toLowerCase(Locale.ROOT);
    }

    record ProvidersResponse(
            EmbeddingProviderResponse embeddings, GenerationProviderResponse generation) {}

    record EmbeddingProviderResponse(
            @Schema(allowableValues = {"none", "fake", "openai"}) String provider,
            @Nullable String model,
            @Nullable Integer dimensions,
            @Schema(
                            description =
                                    "true quando os trechos indexados e as perguntas saem da máquina.")
                    boolean remote) {}

    record GenerationProviderResponse(
            @Schema(allowableValues = {"none", "fake", "openai"}) String provider,
            @Nullable String model,
            @Schema(
                            description =
                                    "true quando a pergunta e os trechos usados como evidência"
                                            + " saem da máquina.")
                    boolean remote) {}
}
