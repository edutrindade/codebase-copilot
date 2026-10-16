package com.codebasecopilot.retrieval;

enum SearchMode {
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
