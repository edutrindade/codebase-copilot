package com.codebasecopilot.answering;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AnsweringProperties.class, PricingProperties.class})
class AnsweringConfiguration {}
