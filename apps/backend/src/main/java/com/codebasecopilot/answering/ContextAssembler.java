package com.codebasecopilot.answering;

import com.codebasecopilot.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Walks the retrieved chunks in fusion order and keeps each one that still fits (P4 of Etapa
// 6). Costs are estimated on the rendered prompt (D-043), so the budget covers instructions,
// question, delimiters and line numbers. No score cutoff: RRF scores are not calibrated (D-059).
final class ContextAssembler {

    record Limits(int maxEvidence, int maxContextTokens, int maxEvidencePerFile) {}

    private ContextAssembler() {}

    static List<ContextEvidence> assemble(
            String question, List<RetrievedChunk> chunks, Limits limits) {
        String nonce = PromptBuilder.placeholderNonce();
        int used =
                estimate(PromptBuilder.instructions(nonce))
                        + estimate(PromptBuilder.questionBlock(question, nonce));
        List<ContextEvidence> selected = new ArrayList<>();
        Map<String, Integer> perFile = new HashMap<>();
        for (RetrievedChunk chunk : chunks) {
            if (selected.size() == limits.maxEvidence()) {
                break;
            }
            if (perFile.getOrDefault(chunk.contentHash(), 0) >= limits.maxEvidencePerFile()
                    || overlapsSelected(chunk, selected)) {
                continue;
            }
            ContextEvidence candidate = new ContextEvidence("E" + (selected.size() + 1), chunk);
            int cost = estimate(PromptBuilder.evidenceBlock(candidate, nonce)) + 1;
            if (used + cost > limits.maxContextTokens()) {
                continue;
            }
            selected.add(candidate);
            perFile.merge(chunk.contentHash(), 1, Integer::sum);
            used += cost;
        }
        return List.copyOf(selected);
    }

    // ⌈characters / 4⌉, the estimate stored with each chunk (D-043).
    static int estimate(String text) {
        return (text.length() + 3) / 4;
    }

    // Chunks overlap by a few lines (lines-v1); a second copy of those lines adds nothing.
    private static boolean overlapsSelected(RetrievedChunk chunk, List<ContextEvidence> selected) {
        for (ContextEvidence evidence : selected) {
            RetrievedChunk other = evidence.chunk();
            if (other.contentHash().equals(chunk.contentHash())
                    && other.startLine() <= chunk.endLine()
                    && chunk.startLine() <= other.endLine()) {
                return true;
            }
        }
        return false;
    }
}
