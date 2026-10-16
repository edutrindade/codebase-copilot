package com.codebasecopilot.shared.localroots;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LocalRootsProperties.class)
class LocalRootsConfiguration {

    @Bean
    LocalRoots localRoots(LocalRootsProperties properties) {
        return LocalRoots.from(properties);
    }
}
