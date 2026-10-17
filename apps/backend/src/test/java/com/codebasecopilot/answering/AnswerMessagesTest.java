package com.codebasecopilot.answering;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;

class AnswerMessagesTest {

    private static final Locale LOCALE = Locale.of("pt", "BR");

    @ParameterizedTest
    @EnumSource(AnswerErrorCode.class)
    void everyErrorCodeHasMessage(AnswerErrorCode code) {
        assertHasMessage("answer.error." + code.name());
    }

    @ParameterizedTest
    @EnumSource(AnswerWarning.class)
    void everyWarningHasMessage(AnswerWarning warning) {
        assertHasMessage("answer.warning." + warning.name());
    }

    @Test
    void abstentionWithoutTextHasMessage() {
        assertHasMessage("answer.abstained");
    }

    @Test
    void invalidCitationsMessageCarriesTheCount() {
        assertThat(
                        messages()
                                .getMessage(
                                        "answer.warning.INVALID_CITATIONS_REMOVED",
                                        new Object[] {3},
                                        LOCALE))
                .contains("3 citação");
    }

    private static void assertHasMessage(String key) {
        assertThat(messages().getMessage(key, new Object[] {1}, null, LOCALE))
                .as("Missing message key %s in messages.properties", key)
                .isNotBlank();
    }

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }
}
