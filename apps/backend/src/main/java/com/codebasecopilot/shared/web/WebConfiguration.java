package com.codebasecopilot.shared.web;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HttpProperties.class)
class WebConfiguration implements WebMvcConfigurer {

    // The packaged UI (D-085), copied to static/ by the with-ui Maven profile. Vite names the
    // files in assets/ by content hash, so they never change; index.html is checked every time.
    // Boot's own static mapping is off (spring.web.resources.add-mappings).
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(
                        CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new SpaResourceResolver());
    }

    // Errors are rendered by the GlobalExceptionHandler, like the ones raised by controllers.
    @Bean
    LocalAccessFilter localAccessFilter(
            HttpProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new LocalAccessFilter(properties, resolver);
    }
}
