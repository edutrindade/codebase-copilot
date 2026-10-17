package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.indexing.content.LineChunker;
import org.junit.jupiter.api.Test;

class EvalChunkingTest {

    @Test
    void absentOrLinesV1ValuesAreLinesV1() {
        assertThat(EvalChunking.parse(null)).isSameAs(LineChunker.V1);
        assertThat(EvalChunking.parse(" ")).isSameAs(LineChunker.V1);
        assertThat(EvalChunking.parse("60:10")).isSameAs(LineChunker.V1);
    }

    @Test
    void otherValuesGetAnEvaluationVersion() {
        assertThat(EvalChunking.parse(" 40:0 "))
                .isEqualTo(new LineChunker.Parameters("eval-lines-40-0", 40, 0));
    }

    @Test
    void rejectsMalformedValues() {
        assertThatThrownBy(() -> EvalChunking.parse("40"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvalChunking.parse("40:-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvalChunking.parse("20:20"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
