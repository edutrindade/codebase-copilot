package com.codebasecopilot.eval;

import com.codebasecopilot.indexing.content.LineChunker;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

// EVAL_CHUNKING=<max lines>:<overlap lines> indexes the corpora with other line-chunking values
// (P3 of Etapa 8), under a version that is never used outside the evaluation.
public final class EvalChunking {

    private static final Pattern VALUE = Pattern.compile("(\\d+):(\\d+)");

    private EvalChunking() {}

    public static LineChunker.Parameters parse(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return LineChunker.V1;
        }
        Matcher matcher = VALUE.matcher(value.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("EVAL_CHUNKING must be <max lines>:<overlap lines>");
        }
        int maxLines = Integer.parseInt(matcher.group(1));
        int overlap = Integer.parseInt(matcher.group(2));
        if (maxLines == LineChunker.MAX_LINES && overlap == LineChunker.OVERLAP_LINES) {
            return LineChunker.V1;
        }
        return new LineChunker.Parameters(
                "eval-lines-" + maxLines + "-" + overlap, maxLines, overlap);
    }

    public static LineChunker.Parameters fromEnvironment() {
        return parse(System.getenv("EVAL_CHUNKING"));
    }
}
