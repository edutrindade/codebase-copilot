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

// With provider=none there is no EmbeddingProvider bean (D-050).
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProviderProperties.class)
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
    EmbeddingRetry embeddingRetry(ProviderProperties properties) {
        return new EmbeddingRetry(
                properties.maxRetries(), INITIAL_BACKOFF, properties.maxRetryWait(), Thread::sleep);
    }

    // Binds like ProviderProperties does, so "OpenAI" or "open-ai" select the same bean. An
    // unknown value matches nothing and is reported by ProviderProperties binding.
    private abstract static class ProviderSelected implements Condition {

        private final Provider provider;

        ProviderSelected(Provider provider) {
            this.provider = provider;
        }

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            try {
                return Binder.get(context.getEnvironment())
                                .bind("app.embeddings.provider", Provider.class)
                                .orElse(Provider.NONE)
                        == provider;
            } catch (BindException e) {
                return false;
            }
        }
    }

    static final class FakeSelected extends ProviderSelected {
        FakeSelected() {
            super(Provider.FAKE);
        }
    }

    static final class OpenAiSelected extends ProviderSelected {
        OpenAiSelected() {
            super(Provider.OPENAI);
        }
    }
}
