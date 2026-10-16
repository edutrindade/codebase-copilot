package com.codebasecopilot.shared;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SharedConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
