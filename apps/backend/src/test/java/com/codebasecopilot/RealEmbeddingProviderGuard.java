package com.codebasecopilot;

import java.util.List;
import java.util.Locale;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

// Registered in META-INF/spring.factories, so it also covers test contexts created later: a
// context that forgets TestProperties.EMBEDDING_PROVIDER would pick the provider from .env.
class RealEmbeddingProviderGuard implements ContextCustomizerFactory {

    static final String PROPERTY = "app.embeddings.provider";

    @Override
    public ContextCustomizer createContextCustomizer(
            Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        return new Customizer();
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
