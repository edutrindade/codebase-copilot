package com.codebasecopilot.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;

class IndexErrorCodeMessagesTest {

    @ParameterizedTest
    @EnumSource(IndexErrorCode.class)
    void everyCodeHasMessage(IndexErrorCode code) {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        String key = "index.error." + code.name();

        assertThat(messages.getMessage(key, null, null, Locale.of("pt", "BR")))
                .as("Missing message key %s in messages.properties", key)
                .isNotBlank();
    }
}
