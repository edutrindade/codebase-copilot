package com.codebasecopilot.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;

class ErrorCodeMessagesTest {

    private static final ResourceBundleMessageSource MESSAGES = messageSource();

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void everyCodeHasTitleAndDetail(ErrorCode code) {
        for (String part : new String[] {"title", "detail"}) {
            String key = "error." + code.name() + "." + part;
            assertThat(MESSAGES.getMessage(key, null, null, GlobalExceptionHandler.LOCALE))
                    .as("Missing message key %s in messages.properties", key)
                    .isNotBlank();
        }
    }

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }
}
