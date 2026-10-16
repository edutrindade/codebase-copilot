package com.codebasecopilot;

import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

// Registered in META-INF/spring.factories, so it also covers test contexts created later: a
// context that forgets TestProperties.EMBEDDING_PROVIDER would pick the provider from .env.
// Only the manual smoke test, excluded from ./mvnw verify by its tag, may call the real provider.
class RealEmbeddingProviderGuard implements ContextCustomizerFactory {

    static final String PROPERTY = "app.embeddings.provider";
    static final String SMOKE_TAG = "smoke";

    @Override
    public @Nullable ContextCustomizer createContextCustomizer(
            Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        return isSmokeTest(testClass) ? null : new Customizer();
    }

    static boolean isSmokeTest(Class<?> testClass) {
        return AnnotatedElementUtils.findMergedRepeatableAnnotations(testClass, Tag.class).stream()
                .anyMatch(tag -> tag.value().equals(SMOKE_TAG));
    }

    static void check(ConfigurableApplicationContext context) {
        String provider = context.getEnvironment().getProperty(PROPERTY, "");
        // Same normalization as Spring's lenient enum binding ("Open-AI" binds to OPENAI).
        if (provider.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT).equals("openai")) {
            throw new IllegalStateException(
                    "Test contexts must not use "
                            + PROPERTY
                            + "=openai; add TestProperties.EMBEDDING_PROVIDER to the context");
        }
    }

    private record Customizer() implements ContextCustomizer {

        @Override
        public void customizeContext(
                ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            check(context);
        }
    }
}
