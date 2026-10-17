package com.codebasecopilot.history;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnswerHistoryTest {

    @Test
    void titleIsTheQuestionOnOneLine() {
        assertThat(AnswerHistory.title("  Como o\n\treembolso   funciona?  "))
                .isEqualTo("Como o reembolso funciona?");
    }

    @Test
    void titleKeepsTheFirstHundredCodePoints() {
        String question = "😀".repeat(99) + "ab";

        assertThat(AnswerHistory.title(question)).isEqualTo("😀".repeat(99) + "a");
    }

    @Test
    void titleDoesNotEndWithTheSpaceBeforeTheCut() {
        assertThat(AnswerHistory.title("a".repeat(99) + " b")).isEqualTo("a".repeat(99));
    }
}
