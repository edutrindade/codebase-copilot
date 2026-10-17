package com.codebasecopilot.eval;

import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.GenerationProperties;
import com.codebasecopilot.providers.GenerationProvider;
import java.nio.file.Path;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

// Wraps the providers of an evaluation context in the vector and answer caches, kept in
// DATA_DIR/eval-cache (or EVAL_CACHE_DIR), so a second run neither pays nor varies.
@TestConfiguration(proxyBeanMethods = false)
public class EvalCaches {

    @Bean
    static BeanPostProcessor evalCaches(Environment environment) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (bean instanceof EmbeddingProvider provider
                        && !(bean instanceof EmbeddingCache)) {
                    return new EmbeddingCache(provider, directory(environment));
                }
                if (bean instanceof GenerationProvider provider
                        && !(bean instanceof GenerationCache)) {
                    String effort =
                            Binder.get(environment)
                                    .bindOrCreate("app.generation", GenerationProperties.class)
                                    .reasoningEffort();
                    return new GenerationCache(
                            provider,
                            directory(environment),
                            effort.isBlank() ? "" : "reasoning-" + effort);
                }
                return bean;
            }
        };
    }

    static Path directory(Environment environment) {
        String configured = environment.getProperty("EVAL_CACHE_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        String dataDir = environment.resolvePlaceholders("${DATA_DIR:~/.codebase-copilot}").strip();
        if (dataDir.equals("~") || dataDir.startsWith("~/")) {
            dataDir = System.getProperty("user.home") + dataDir.substring(1);
        }
        return Path.of(dataDir, "eval-cache");
    }
}
