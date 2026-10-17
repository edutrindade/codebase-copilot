package com.codebasecopilot.retrieval;

public enum SearchMode {
    LEXICAL,
    VECTOR,
    HYBRID;

    boolean usesLexical() {
        return this != VECTOR;
    }

    boolean usesVector() {
        return this != LEXICAL;
    }
}
