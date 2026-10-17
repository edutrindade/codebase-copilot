package com.codebasecopilot.providers;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.providers.GenerationRequest.OutputSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FakeGenerationProviderTest {

    private static final OutputSchema SCHEMA = new OutputSchema("answer", Map.of());

    private final FakeGenerationProvider provider = new FakeGenerationProvider("demo");

    @Test
    void citesTheFirstEvidenceLabel() {
        GenerationResult result =
                provider.generate(
                        new GenerationRequest("Answer.", "<E3> x </E3> <E1> y </E1>", SCHEMA));

        assertThat(result.output())
                .isEqualTo(
                        "{\"status\":\"ANSWERED\",\"answer\":\"Resposta de demonstração baseada"
                                + " em [E3].\",\"citations\":[{\"evidence\":\"E3\","
                                + "\"startLine\":null,\"endLine\":null}]}");
    }

    @Test
    void abstainsWithoutEvidence() {
        GenerationResult result =
                provider.generate(new GenerationRequest("Answer.", "no labels here", SCHEMA));

        assertThat(result.output())
                .isEqualTo(
                        "{\"status\":\"ABSTAINED\",\"answer\":\"Não há evidências suficientes"
                                + " para responder.\",\"citations\":[]}");
    }

    @Test
    void doesNotTakeOtherWordsForLabels() {
        GenerationResult result =
                provider.generate(new GenerationRequest("Answer.", "E0 E12x MODE1 e1", SCHEMA));

        assertThat(result.output()).contains("ABSTAINED");
    }

    @Test
    void reportsTermCountsAsTokens() {
        GenerationResult result =
                provider.generate(new GenerationRequest("Answer now.", "one two E1", SCHEMA));

        assertThat(result.inputTokens()).isEqualTo(5L);
        assertThat(result.outputTokens()).isPositive();
        assertThat(result.reasoningTokens()).isZero();
    }

    @Test
    void isDeterministic() {
        GenerationRequest request = new GenerationRequest("Answer.", "E2 text", SCHEMA);

        assertThat(provider.generate(request)).isEqualTo(provider.generate(request));
    }

    @Test
    void reportsModel() {
        assertThat(provider.model()).isEqualTo(new GenerationModel("fake", "demo"));
    }
}
