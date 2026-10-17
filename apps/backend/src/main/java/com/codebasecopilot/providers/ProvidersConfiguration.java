package com.codebasecopilot.providers;

import com.codebasecopilot.providers.ProviderProperties.Provider;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

// With provider=none there is no EmbeddingProvider (D-050) or GenerationProvider bean.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ProviderProperties.class, GenerationProperties.class})
class ProvidersConfiguration {

    static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);

    @Bean
    @Conditional(FakeSelected.class)
    EmbeddingProvider fakeEmbeddingProvider(ProviderProperties properties) {
        return new FakeEmbeddingProvider(properties.model(), properties.dimensions());
    }

    @Bean
    @Conditional(OpenAiSelected.class)
    EmbeddingProvider openAiEmbeddingProvider(ProviderProperties properties, Clock clock) {
        return new OpenAiEmbeddingProvider(properties, clock);
    }

    @Bean
    @Conditional(FakeGenerationSelected.class)
    GenerationProvider fakeGenerationProvider(GenerationProperties properties) {
        return new FakeGenerationProvider(properties.model());
    }

    @Bean
    @Conditional(OpenAiGenerationSelected.class)
    GenerationProvider openAiGenerationProvider(GenerationProperties properties) {
        return new OpenAiGenerationProvider(properties);
    }

    @Bean
    EmbeddingRetry embeddingRetry(ProviderProperties properties) {
        return new EmbeddingRetry(
                properties.maxRetries(), INITIAL_BACKOFF, properties.maxRetryWait(), Thread::sleep);
    }

    // Binds like the properties records do, so "OpenAI" or "open-ai" select the same bean. An
    // unknown value matches nothing and is reported when the properties are bound.
    private abstract static class ProviderSelected implements Condition {

        static final String EMBEDDINGS = "app.embeddings.provider";
        static final String GENERATION = "app.generation.provider";

        private final String property;
        private final Provider provider;

        ProviderSelected(String property, Provider provider) {
            this.property = property;
            this.provider = provider;
        }

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            try {
                return Binder.get(context.getEnvironment())
                                .bind(property, Provider.class)
                                .orElse(Provider.NONE)
                        == provider;
            } catch (BindException e) {
                return false;
            }
        }
    }

    static final class FakeSelected extends ProviderSelected {
        FakeSelected() {
            super(EMBEDDINGS, Provider.FAKE);
        }
    }

    static final class OpenAiSelected extends ProviderSelected {
        OpenAiSelected() {
            super(EMBEDDINGS, Provider.OPENAI);
        }
    }

    static final class FakeGenerationSelected extends ProviderSelected {
        FakeGenerationSelected() {
            super(GENERATION, Provider.FAKE);
        }
    }

    static final class OpenAiGenerationSelected extends ProviderSelected {
        OpenAiGenerationSelected() {
            super(GENERATION, Provider.OPENAI);
        }
    }
}
