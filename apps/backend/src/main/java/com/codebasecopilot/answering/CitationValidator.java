package com.codebasecopilot.answering;

import com.codebasecopilot.history.AnswerStatus;
import com.codebasecopilot.history.EvidenceValidation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Checks the model output against the evidence sent (P6 of Etapa 6, D-011): labels and lines
// must exist; whether a citation supports the statement is measured in the evaluation. The
// final status is decided here, not by the model.
final class CitationValidator {

    sealed interface Outcome permits Valid, Invalid {}

    // Output that is not JSON or does not match the schema; nothing of it is shown.
    record Invalid() implements Outcome {}

    record Valid(
            AnswerStatus status,
            String text,
            List<AnswerWarning> warnings,
            int invalidCitationCount,
            Map<String, Check> checks)
            implements Outcome {

        Valid {
            warnings = List.copyOf(warnings);
            checks = Map.copyOf(checks);
        }
    }

    // Lines are null for a citation of the whole evidence, or for an evidence not cited.
    record Check(
            EvidenceValidation validation,
            @Nullable Integer citedStartLine,
            @Nullable Integer citedEndLine) {

        static final Check NOT_CITED = new Check(EvidenceValidation.NOT_CITED, null, null);
        static final Check INVALID_RANGE = new Check(EvidenceValidation.INVALID_RANGE, null, null);
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> FIELDS = Set.of("status", "answer", "citations");
    private static final Set<String> CITATION_FIELDS = Set.of("evidence", "startLine", "endLine");
    private static final Pattern MARKER = Pattern.compile("\\s*\\[(E[1-9][0-9]*)]");

    private record Citation(String label, @Nullable Integer startLine, @Nullable Integer endLine) {}

    private record Parsed(AnswerStatus status, String answer, List<Citation> citations) {}

    private CitationValidator() {}

    static Outcome validate(String output, List<ContextEvidence> evidence) {
        Parsed parsed = parse(output);
        if (parsed == null) {
            return new Invalid();
        }
        Map<String, ContextEvidence> byLabel = new LinkedHashMap<>();
        evidence.forEach(item -> byLabel.put(item.label(), item));

        int invalid = 0;
        Map<String, List<int[]>> ranges = new HashMap<>();
        Set<String> wholeCited = new HashSet<>();
        Set<String> badRange = new HashSet<>();
        Set<String> referenced = new HashSet<>();
        for (Citation citation : parsed.citations()) {
            referenced.add(citation.label());
            ContextEvidence item = byLabel.get(citation.label());
            if (item == null) {
                invalid++;
                continue;
            }
            if (citation.startLine() == null && citation.endLine() == null) {
                wholeCited.add(citation.label());
                continue;
            }
            int start =
                    citation.startLine() == null ? item.chunk().startLine() : citation.startLine();
            int end = citation.endLine() == null ? item.chunk().endLine() : citation.endLine();
            if (start > end || start < item.chunk().startLine() || end > item.chunk().endLine()) {
                invalid++;
                badRange.add(citation.label());
                continue;
            }
            ranges.computeIfAbsent(citation.label(), label -> new ArrayList<>())
                    .add(new int[] {start, end});
        }

        Map<String, Check> checks = new HashMap<>();
        for (String label : byLabel.keySet()) {
            checks.put(label, check(label, wholeCited, ranges, badRange));
        }

        StringBuilder text = new StringBuilder();
        Set<String> unsupportedMarkers = new HashSet<>();
        Matcher marker = MARKER.matcher(parsed.answer());
        while (marker.find()) {
            String label = marker.group(1);
            Check check = checks.get(label);
            boolean cited = check != null && check.validation() == EvidenceValidation.CITED;
            if (!cited && !referenced.contains(label)) {
                unsupportedMarkers.add(label);
            }
            marker.appendReplacement(text, cited ? Matcher.quoteReplacement(marker.group()) : "");
        }
        marker.appendTail(text);
        invalid += unsupportedMarkers.size();

        boolean anyCited =
                checks.values().stream()
                        .anyMatch(check -> check.validation() == EvidenceValidation.CITED);
        List<AnswerWarning> warnings = new ArrayList<>();
        if (invalid > 0) {
            warnings.add(AnswerWarning.INVALID_CITATIONS_REMOVED);
        }
        AnswerStatus status = parsed.status();
        if (status != AnswerStatus.ABSTAINED && !anyCited) {
            status = AnswerStatus.PARTIAL;
            warnings.add(AnswerWarning.NO_VALID_CITATIONS);
        }
        return new Valid(status, text.toString().strip(), warnings, invalid, checks);
    }

    // A valid citation wins over an invalid one of the same evidence; several narrowed
    // citations become the lines that cover them all.
    private static Check check(
            String label,
            Set<String> wholeCited,
            Map<String, List<int[]>> ranges,
            Set<String> badRange) {
        if (wholeCited.contains(label)) {
            return new Check(EvidenceValidation.CITED, null, null);
        }
        List<int[]> cited = ranges.get(label);
        if (cited != null) {
            int start = cited.stream().mapToInt(range -> range[0]).min().orElseThrow();
            int end = cited.stream().mapToInt(range -> range[1]).max().orElseThrow();
            return new Check(EvidenceValidation.CITED, start, end);
        }
        return badRange.contains(label) ? Check.INVALID_RANGE : Check.NOT_CITED;
    }

    // Null when the output is not JSON or does not match PromptBuilder.SCHEMA. An answer that
    // claims to answer with no text is invalid too: there would be nothing to show.
    private static @Nullable Parsed parse(String output) {
        JsonNode root;
        try {
            root = JSON.readTree(output);
        } catch (JacksonException e) {
            return null;
        }
        if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(FIELDS)) {
            return null;
        }
        AnswerStatus status = status(root.get("status"));
        JsonNode answer = root.get("answer");
        JsonNode citations = root.get("citations");
        if (status == null || !answer.isString() || !citations.isArray()) {
            return null;
        }
        if (status != AnswerStatus.ABSTAINED && answer.stringValue().isBlank()) {
            return null;
        }
        List<Citation> parsed = new ArrayList<>();
        for (JsonNode citation : citations) {
            if (!citation.isObject()
                    || !Set.copyOf(citation.propertyNames()).equals(CITATION_FIELDS)
                    || !citation.get("evidence").isString()
                    || !isLine(citation.get("startLine"))
                    || !isLine(citation.get("endLine"))) {
                return null;
            }
            parsed.add(
                    new Citation(
                            citation.get("evidence").stringValue(),
                            line(citation.get("startLine")),
                            line(citation.get("endLine"))));
        }
        return new Parsed(status, answer.stringValue(), parsed);
    }

    private static @Nullable AnswerStatus status(JsonNode node) {
        if (!node.isString()) {
            return null;
        }
        return switch (node.stringValue()) {
            case "ANSWERED" -> AnswerStatus.ANSWERED;
            case "PARTIAL" -> AnswerStatus.PARTIAL;
            case "ABSTAINED" -> AnswerStatus.ABSTAINED;
            default -> null;
        };
    }

    private static boolean isLine(JsonNode node) {
        return node.isNull() || (node.isIntegralNumber() && node.canConvertToInt());
    }

    private static @Nullable Integer line(JsonNode node) {
        return node.isNull() ? null : node.intValue();
    }
}
