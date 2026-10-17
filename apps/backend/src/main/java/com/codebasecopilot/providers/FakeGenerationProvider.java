package com.codebasecopilot.providers;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Deterministic answers for tests and demos (D-019), in the answer schema of answering: cites the
// first evidence label (E1, E2…) found in the input, or abstains when there is none. Tokens are
// term counts.
final class FakeGenerationProvider implements GenerationProvider {

    static final String NAME = "fake";

    private static final Pattern LABEL = Pattern.compile("\\bE[1-9][0-9]*\\b");
    private static final Pattern TERM = Pattern.compile("[\\p{L}\\p{N}_]+");

    private final GenerationModel model;

    FakeGenerationProvider(String model) {
        this.model = new GenerationModel(NAME, model);
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        Matcher label = LABEL.matcher(request.input());
        String output =
                label.find()
                        ? """
                          {"status":"ANSWERED","answer":"Resposta de demonstração baseada em [%s].",\
                          "citations":[{"evidence":"%s","startLine":null,"endLine":null}]}"""
                                .formatted(label.group(), label.group())
                        : """
                          {"status":"ABSTAINED","answer":"Não há evidências suficientes para \
                          responder.","citations":[]}""";
        return new GenerationResult(
                output, terms(request.instructions()) + terms(request.input()), terms(output), 0L);
    }

    @Override
    public GenerationModel model() {
        return model;
    }

    private static long terms(String text) {
        return TERM.matcher(text.toLowerCase(Locale.ROOT)).results().count();
    }
}
