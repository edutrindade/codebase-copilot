package com.codebasecopilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestPropertySource;

class RealProviderGuardTest {

    private static final String EMBEDDINGS = "app.embeddings.provider";
    private static final String GENERATION = "app.generation.provider";

    @Test
    void isRegisteredForEveryTestContext() {
        assertThat(SpringFactoriesLoader.loadFactories(ContextCustomizerFactory.class, null))
                .hasAtLeastOneElementOfType(RealProviderGuard.class);
    }

    @Test
    void failsTestContextConfiguredWithOpenAiEmbeddings() {
        TestContextManager manager = new TestContextManager(OpenAiEmbeddingsContext.class);

        assertThatThrownBy(() -> manager.prepareTestInstance(new OpenAiEmbeddingsContext()))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not use app.embeddings.provider=openai");
    }

    @Test
    void failsTestContextConfiguredWithOpenAiGeneration() {
        TestContextManager manager = new TestContextManager(OpenAiGenerationContext.class);

        assertThatThrownBy(() -> manager.prepareTestInstance(new OpenAiGenerationContext()))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not use app.generation.provider=openai");
    }

    @Test
    void exemptsOnlyTestsTaggedSmokeOrEval() {
        RealProviderGuard guard = new RealProviderGuard();

        assertThat(guard.createContextCustomizer(SmokeContext.class, List.of())).isNull();
        assertThat(guard.createContextCustomizer(EvalContext.class, List.of())).isNull();
        assertThat(guard.createContextCustomizer(PerformanceContext.class, List.of())).isNotNull();
        assertThat(guard.createContextCustomizer(OpenAiEmbeddingsContext.class, List.of()))
                .isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
        EMBEDDINGS + ", openai",
        EMBEDDINGS + ", OPENAI",
        EMBEDDINGS + ", Open-AI",
        GENERATION + ", openai",
        GENERATION + ", OPENAI",
        GENERATION + ", Open-AI"
    })
    void rejectsOpenAi(String property, String provider) {
        assertThatThrownBy(() -> RealProviderGuard.check(context(property, provider)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not use " + property + "=openai");
    }

    @ParameterizedTest
    @ValueSource(strings = {"fake", "none", ""})
    void acceptsOtherProviders(String provider) {
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty(EMBEDDINGS, provider)
                        .withProperty(GENERATION, provider);

        assertThatCode(() -> RealProviderGuard.check(context(environment)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsMissingProperties() {
        assertThatCode(() -> RealProviderGuard.check(context(new MockEnvironment())))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({EMBEDDINGS + ", EMBEDDING_PROVIDER", GENERATION + ", GENERATION_PROVIDER"})
    void resolvesProviderFromEnvironmentPlaceholder(String property, String variable) {
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty(property, "${" + variable + ":none}")
                        .withProperty(variable, "openai");

        assertThatThrownBy(() -> RealProviderGuard.check(context(environment)))
                .isInstanceOf(IllegalStateException.class);
    }

    @ContextConfiguration(classes = OpenAiEmbeddingsContext.class)
    @TestPropertySource(properties = EMBEDDINGS + "=openai")
    static class OpenAiEmbeddingsContext {}

    @ContextConfiguration(classes = OpenAiGenerationContext.class)
    @TestPropertySource(properties = GENERATION + "=openai")
    static class OpenAiGenerationContext {}

    @Tag("smoke")
    static class SmokeContext {}

    @Tag("eval")
    static class EvalContext {}

    @Tag("performance")
    static class PerformanceContext {}

    private static GenericApplicationContext context(String property, String provider) {
        return context(new MockEnvironment().withProperty(property, provider));
    }

    private static GenericApplicationContext context(MockEnvironment environment) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        return context;
    }
}
