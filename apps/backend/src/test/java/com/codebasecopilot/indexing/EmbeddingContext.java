package com.codebasecopilot.indexing;

import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.EmbeddingRetry;
import com.codebasecopilot.providers.ProviderProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// One extra context for the embedding stage: a spy on the fake provider, small batches and job
// limit, and retry waits that move a shared clock forward instead of sleeping (D-051).
@IntegrationTest
@TestPropertySource(
        properties = {
            "app.embeddings.batch-size=4",
            "app.embeddings.max-chunks-per-job=20",
            "app.indexing.heartbeat-interval=10s"
        })
@Import(EmbeddingContext.ControlledTime.class)
abstract class EmbeddingContext {

    @MockitoSpyBean EmbeddingProvider provider;

    static final class ControlledClock extends Clock {

        private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

        void advance(Duration duration) {
            offset.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public Instant instant() {
            return Instant.now().plus(offset.get());
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledTime {

        @Bean
        @Primary
        ControlledClock controlledClock() {
            return new ControlledClock();
        }

        // A short real pause per slice gives the dispatcher the chance to take over a job
        // whose lease was not renewed.
        @Bean
        @Primary
        EmbeddingRetry controlledEmbeddingRetry(
                ProviderProperties properties, ControlledClock clock) {
            return new EmbeddingRetry(
                    properties.maxRetries(),
                    Duration.ofSeconds(1),
                    properties.maxRetryWait(),
                    duration -> {
                        clock.advance(duration);
                        Thread.sleep(20);
                    });
        }
    }
}
