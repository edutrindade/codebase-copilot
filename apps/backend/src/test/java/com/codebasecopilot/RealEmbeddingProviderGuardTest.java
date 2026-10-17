package com.codebasecopilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestPropertySource;

class RealEmbeddingProviderGuardTest {

    @Test
    void isRegisteredForEveryTestContext() {
        assertThat(SpringFactoriesLoader.loadFactories(ContextCustomizerFactory.class, null))
                .hasAtLeastOneElementOfType(RealEmbeddingProviderGuard.class);
    }

    @Test
    void failsTestContextConfiguredWithOpenAi() {
        TestContextManager manager = new TestContextManager(OpenAiContext.class);

        assertThatThrownBy(() -> manager.prepareTestInstance(new OpenAiContext()))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not use app.embeddings.provider=openai");
    }

    @Test
    void exemptsOnlyTestsTaggedSmokeOrEval() {
        RealEmbeddingProviderGuard guard = new RealEmbeddingProviderGuard();

        assertThat(guard.createContextCustomizer(SmokeContext.class, List.of())).isNull();
        assertThat(guard.createContextCustomizer(EvalContext.class, List.of())).isNull();
        assertThat(guard.createContextCustomizer(PerformanceContext.class, List.of())).isNotNull();
        assertThat(guard.createContextCustomizer(OpenAiContext.class, List.of())).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "OPENAI", "Open-AI"})
    void rejectsOpenAi(String provider) {
        assertThatThrownBy(() -> RealEmbeddingProviderGuard.check(context(provider)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not use app.embeddings.provider=openai");
    }

    @ParameterizedTest
    @ValueSource(strings = {"fake", "none", ""})
    void acceptsOtherProviders(String provider) {
        assertThatCode(() -> RealEmbeddingProviderGuard.check(context(provider)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsMissingProperty() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(new MockEnvironment());

        assertThatCode(() -> RealEmbeddingProviderGuard.check(context)).doesNotThrowAnyException();
    }

    @Test
    void resolvesProviderFromEnvironmentPlaceholder() {
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty(
                                RealEmbeddingProviderGuard.PROPERTY, "${EMBEDDING_PROVIDER:none}")
                        .withProperty("EMBEDDING_PROVIDER", "openai");
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);

        assertThatThrownBy(() -> RealEmbeddingProviderGuard.check(context))
                .isInstanceOf(IllegalStateException.class);
    }

    @ContextConfiguration(classes = OpenAiContext.class)
    @TestPropertySource(properties = "app.embeddings.provider=openai")
    static class OpenAiContext {}

    @Tag("smoke")
    static class SmokeContext {}

    @Tag("eval")
    static class EvalContext {}

    @Tag("performance")
    static class PerformanceContext {}

    private static GenericApplicationContext context(String provider) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(
                new MockEnvironment().withProperty(RealEmbeddingProviderGuard.PROPERTY, provider));
        return context;
    }
}
