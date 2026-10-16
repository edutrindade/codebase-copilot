package com.codebasecopilot.importjob;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.unit.DataSize;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImportProperties.class)
class ImportConfiguration {

    @Bean
    ZipExtractor zipExtractor(ImportProperties properties, Environment environment) {
        requireMultipartLimit(environment, "max-file-size", properties.maxZipBytes());
        requireMultipartLimit(environment, "max-request-size", properties.maxZipBytes());
        return new ZipExtractor(properties.limits());
    }

    // A smaller multipart limit would reject allowed uploads before they reach the service.
    private static void requireMultipartLimit(Environment environment, String name, DataSize max) {
        String property = "spring.servlet.multipart." + name;
        DataSize limit =
                Binder.get(environment)
                        .bind(property, DataSize.class)
                        .orElse(DataSize.ofMegabytes(1));
        if (limit.toBytes() >= 0 && limit.toBytes() < max.toBytes()) {
            throw new IllegalStateException(
                    property + " must not be smaller than app.import.max-zip-bytes (D-028)");
        }
    }
}
