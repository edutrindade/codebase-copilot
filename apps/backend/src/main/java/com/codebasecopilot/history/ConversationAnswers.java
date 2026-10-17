package com.codebasecopilot.history;

import java.util.List;

// Answers in the order of their questions, each with its evidence.
public record ConversationAnswers(StoredConversation conversation, List<StoredAnswer> answers) {

    public ConversationAnswers {
        answers = List.copyOf(answers);
    }
}
