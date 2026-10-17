package com.codebasecopilot.providers;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.providers.GenerationRequest.OutputSchema;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Manual smoke test against the real chat provider (D-063), outside ./mvnw verify. It sends one
// short synthetic snippet and question; the key comes from GENERATION_API_KEY (.env or
// environment):
// ./mvnw verify -Dtest.excluded-groups= -Dgroups=smoke -Dtest=none
//     -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=GenerationSmokeIT
@Tag("smoke")
@SpringBootTest(
        classes = ProvidersConfiguration.class,
        properties = {"app.embeddings.provider=none", "app.generation.provider=openai"})
class GenerationSmokeIT {

    private static final OutputSchema SCHEMA =
            new OutputSchema(
                    "answer",
                    Map.of(
                            "type",
                            "object",
                            "properties",
                            Map.of(
                                    "status",
                                            Map.of(
                                                    "type",
                                                    "string",
                                                    "enum",
                                                    List.of("ANSWERED", "ABSTAINED")),
                                    "answer", Map.of("type", "string"),
                                    "evidence", Map.of("type", List.of("string", "null"))),
                            "required",
                            List.of("status", "answer", "evidence"),
                            "additionalProperties",
                            false));

    @Autowired private GenerationProvider provider;
    @Autowired private GenerationProperties properties;

    @Test
    void answersWithTheStrictSchema() {
        long start = System.nanoTime();
        GenerationResult result =
                provider.generate(
                        new GenerationRequest(
                                "Answer only from the evidence. Cite the evidence label you used,"
                                        + " or abstain if the evidence does not answer.",
                                """
                                <evidence id="E1" path="src/billing/Refund.java" lines="1-3">
                                class Refund {
                                    long amount(Order o) { return o.total() - o.fee(); }
                                }
                                </evidence>
                                Question: how is the refund amount calculated?""",
                                SCHEMA));
        long millis = (System.nanoTime() - start) / 1_000_000;

        JsonNode output = JsonMapper.builder().build().readTree(result.output());
        assertThat(output.get("status").asString()).isEqualTo("ANSWERED");
        assertThat(output.get("evidence").asString()).isEqualTo("E1");
        assertThat(output.get("answer").asString()).isNotBlank();
        assertThat(result.inputTokens()).isPositive();
        assertThat(result.outputTokens()).isPositive();
        System.out.printf(
                Locale.ROOT,
                "Generation smoke test %s: %s (reasoning %s), %d input tokens, %d output tokens"
                        + " (%s reasoning), %d ms%n",
                LocalDate.now(),
                properties.model(),
                properties.reasoningEffort(),
                result.inputTokens(),
                result.outputTokens(),
                result.reasoningTokens(),
                millis);
    }
}
