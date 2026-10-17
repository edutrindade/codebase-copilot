package com.codebasecopilot.shared.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HttpProperties.class)
class WebConfiguration {

    // Errors are rendered by the GlobalExceptionHandler, like the ones raised by controllers.
    @Bean
    LocalAccessFilter localAccessFilter(
            HttpProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new LocalAccessFilter(properties, resolver);
    }
}
