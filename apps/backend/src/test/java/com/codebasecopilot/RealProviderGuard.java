package com.codebasecopilot;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

// Registered in META-INF/spring.factories, so it also covers test contexts created later: a
// context that forgets TestProperties.EMBEDDING_PROVIDER or GENERATION_PROVIDER would pick the
// provider from .env. Only the manual smoke tests and the evaluation harnesses, excluded from
// ./mvnw verify by their tags, may call a real provider.
class RealProviderGuard implements ContextCustomizerFactory {

    static final List<String> PROPERTIES =
            List.of("app.embeddings.provider", "app.generation.provider");
    static final Set<String> REAL_PROVIDER_TAGS = Set.of("smoke", "eval");

    @Override
    public @Nullable ContextCustomizer createContextCustomizer(
            Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        return mayUseRealProvider(testClass) ? null : new Customizer();
    }

    static boolean mayUseRealProvider(Class<?> testClass) {
        return AnnotatedElementUtils.findMergedRepeatableAnnotations(testClass, Tag.class).stream()
                .anyMatch(tag -> REAL_PROVIDER_TAGS.contains(tag.value()));
    }

    static void check(ConfigurableApplicationContext context) {
        for (String property : PROPERTIES) {
            String provider = context.getEnvironment().getProperty(property, "");
            // Same normalization as Spring's lenient enum binding ("Open-AI" binds to OPENAI).
            if (provider.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT).equals("openai")) {
                throw new IllegalStateException(
                        "Test contexts must not use "
                                + property
                                + "=openai; add the provider from TestProperties to the context");
            }
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
