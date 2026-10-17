package com.codebasecopilot.history;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// Only a PENDING answer is finished, so the recovery and a late completion never both win.
@Repository
class AnswerRepository {

    private static final TypeReference<Map<String, Object>> METADATA = new TypeReference<>() {};

    private static final String ANSWER_COLUMNS =
            """
            SELECT a.id, c.project_id, c.id AS conversation_id,
                   q.content AS question, a.snapshot_id, a.status,
                   r.content AS text, a.error_code, a.warnings,
                   a.invalid_citation_count, a.retrieval_mode, a.prompt_version,
                   a.generation_provider, a.generation_model,
                   a.retrieval_metadata::text AS retrieval_metadata, a.latency_ms,
                   a.input_tokens, a.output_tokens, a.estimated_cost,
                   a.cost_currency, a.cost_status, a.created_at, a.completed_at
            FROM answers a
            JOIN messages q ON q.id = a.question_message_id
            JOIN conversations c ON c.id = q.conversation_id
            LEFT JOIN messages r ON r.id = a.answer_message_id
            """;

    private static final String EVIDENCE_COLUMNS =
            """
            SELECT e.id, e.answer_id, e.snapshot_id, e.chunk_id, e.citation_label,
                   e.relative_path, e.other_paths, e.content_hash, e.start_line, e.end_line,
                   e.cited_start_line, e.cited_end_line, e.snippet, e.rank_lexical,
                   e.rank_vector, e.rank_path, e.final_rank, e.validation_status
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AnswerRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void insertPending(UUID id, UUID questionMessageId, NewQuestion question, Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO answers (id, question_message_id, snapshot_id, status,
                                             retrieval_mode, prompt_version, created_at)
                        VALUES (:id, :questionMessageId, :snapshotId, 'PENDING', :mode,
                                :promptVersion, :now)
                        """)
                .param("id", id)
                .param("questionMessageId", questionMessageId)
                .param("snapshotId", question.snapshotId())
                .param("mode", question.retrievalMode())
                .param("promptVersion", question.promptVersion())
                .param("now", Timestamps.of(now))
                .update();
    }

    // Locks the answer until the transaction ends; empty when it is gone or no longer PENDING.
    Optional<UUID> lockPendingConversation(UUID answerId) {
        return jdbc.sql(
                        """
                        SELECT m.conversation_id
                        FROM answers a
                        JOIN messages m ON m.id = a.question_message_id
                        WHERE a.id = :id AND a.status = 'PENDING'
                        FOR UPDATE OF a
                        """)
                .param("id", answerId)
                .query(UUID.class)
                .optional();
    }

    void finish(UUID id, @Nullable UUID answerMessageId, AnswerOutcome outcome, Instant now) {
        AnswerMetrics metrics = outcome.metrics();
        jdbc.sql(
                        """
                        UPDATE answers
                        SET status = :status, answer_message_id = :answerMessageId,
                            error_code = :errorCode, warnings = :warnings,
                            invalid_citation_count = :invalidCitations,
                            generation_provider = :provider, generation_model = :model,
                            retrieval_metadata = CAST(:metadata AS jsonb),
                            latency_ms = :latencyMs, input_tokens = :inputTokens,
                            output_tokens = :outputTokens, estimated_cost = :cost,
                            cost_currency = :currency, cost_status = :costStatus,
                            completed_at = :now
                        WHERE id = :id AND status = 'PENDING'
                        """)
                .param("id", id)
                .param("status", outcome.status().name())
                .param("answerMessageId", answerMessageId)
                .param("errorCode", outcome.errorCode())
                .param("warnings", outcome.warnings().toArray(String[]::new))
                .param("invalidCitations", outcome.invalidCitationCount())
                .param("provider", outcome.generationProvider())
                .param("model", outcome.generationModel())
                .param(
                        "metadata",
                        outcome.retrievalMetadata() == null
                                ? null
                                : json.writeValueAsString(outcome.retrievalMetadata()))
                .param("latencyMs", metrics.latencyMs())
                .param("inputTokens", metrics.inputTokens())
                .param("outputTokens", metrics.outputTokens())
                .param("cost", metrics.estimatedCost())
                .param("currency", metrics.costCurrency())
                .param("costStatus", metrics.costStatus().name())
                .param("now", Timestamps.of(now))
                .update();
    }

    // The snapshot is the answer's, so an evidence never points at another one.
    void insertEvidence(UUID id, UUID answerId, AnswerEvidence evidence) {
        jdbc.sql(
                        """
                        INSERT INTO answer_evidence (id, answer_id, snapshot_id, chunk_id,
                            citation_label, relative_path, other_paths, content_hash,
                            start_line, end_line, cited_start_line, cited_end_line, snippet,
                            rank_lexical, rank_vector, rank_path, final_rank, validation_status)
                        SELECT :id, a.id, a.snapshot_id, :chunkId, :label, :path, :otherPaths,
                               :contentHash, :startLine, :endLine, :citedStartLine,
                               :citedEndLine, :snippet, :rankLexical, :rankVector, :rankPath,
                               :finalRank, :validation
                        FROM answers a
                        WHERE a.id = :answerId
                        """)
                .param("id", id)
                .param("answerId", answerId)
                .param("chunkId", evidence.chunkId())
                .param("label", evidence.citationLabel())
                .param("path", evidence.relativePath())
                .param("otherPaths", evidence.otherPaths().toArray(String[]::new))
                .param("contentHash", evidence.contentHash())
                .param("startLine", evidence.startLine())
                .param("endLine", evidence.endLine())
                .param("citedStartLine", evidence.citedStartLine())
                .param("citedEndLine", evidence.citedEndLine())
                .param("snippet", evidence.snippet())
                .param("rankLexical", evidence.rankLexical())
                .param("rankVector", evidence.rankVector())
                .param("rankPath", evidence.rankPath())
                .param("finalRank", evidence.finalRank())
                .param("validation", evidence.validation().name())
                .update();
    }

    int failPendingCreatedBefore(Instant cutoff, String errorCode, Instant now) {
        return jdbc.sql(
                        """
                        UPDATE answers
                        SET status = 'FAILED', error_code = :code, completed_at = :now
                        WHERE status = 'PENDING' AND created_at < :cutoff
                        """)
                .param("cutoff", Timestamps.of(cutoff))
                .param("code", errorCode)
                .param("now", Timestamps.of(now))
                .update();
    }

    Optional<StoredAnswer> findById(UUID id) {
        return jdbc.sql(ANSWER_COLUMNS + "WHERE a.id = :id")
                .param("id", id)
                .query(this::mapAnswer)
                .optional()
                .map(answer -> answer.withEvidence(evidenceOf(id)));
    }

    // In the order of the questions; PENDING and FAILED answers included.
    List<StoredAnswer> findByConversation(UUID conversationId) {
        List<StoredAnswer> answers =
                jdbc.sql(
                                ANSWER_COLUMNS
                                        + """
                                        WHERE q.conversation_id = :conversationId
                                        ORDER BY q.created_at, q.id
                                        """)
                        .param("conversationId", conversationId)
                        .query(this::mapAnswer)
                        .list();
        Map<UUID, List<StoredEvidence>> evidence =
                jdbc
                        .sql(
                                EVIDENCE_COLUMNS
                                        + """
                                        FROM answer_evidence e
                                        JOIN answers a ON a.id = e.answer_id
                                        JOIN messages q ON q.id = a.question_message_id
                                        WHERE q.conversation_id = :conversationId
                                        ORDER BY e.final_rank, e.citation_label
                                        """)
                        .param("conversationId", conversationId)
                        .query(AnswerRepository::mapEvidence)
                        .list()
                        .stream()
                        .collect(Collectors.groupingBy(StoredEvidence::answerId));
        return answers.stream()
                .map(answer -> answer.withEvidence(evidence.getOrDefault(answer.id(), List.of())))
                .toList();
    }

    // Empty when the evidence belongs to another project.
    Optional<StoredEvidence> findEvidence(UUID projectId, UUID evidenceId) {
        return jdbc.sql(
                        EVIDENCE_COLUMNS
                                + """
                                FROM answer_evidence e
                                JOIN answers a ON a.id = e.answer_id
                                JOIN messages q ON q.id = a.question_message_id
                                JOIN conversations c ON c.id = q.conversation_id
                                WHERE e.id = :id AND c.project_id = :projectId
                                """)
                .param("id", evidenceId)
                .param("projectId", projectId)
                .query(AnswerRepository::mapEvidence)
                .optional();
    }

    private List<StoredEvidence> evidenceOf(UUID answerId) {
        return jdbc.sql(
                        EVIDENCE_COLUMNS
                                + """
                                FROM answer_evidence e
                                WHERE e.answer_id = :answerId
                                ORDER BY e.final_rank, e.citation_label
                                """)
                .param("answerId", answerId)
                .query(AnswerRepository::mapEvidence)
                .list();
    }

    // Without evidence, which is read after the answer row, outside its result set.
    private StoredAnswer mapAnswer(ResultSet rs, int rowNum) throws SQLException {
        String metadata = rs.getString("retrieval_metadata");
        return new StoredAnswer(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("conversation_id", UUID.class),
                rs.getString("question"),
                rs.getObject("snapshot_id", UUID.class),
                AnswerStatus.valueOf(rs.getString("status")),
                rs.getString("text"),
                rs.getString("error_code"),
                strings(rs.getArray("warnings")),
                rs.getInt("invalid_citation_count"),
                rs.getString("retrieval_mode"),
                rs.getString("prompt_version"),
                rs.getString("generation_provider"),
                rs.getString("generation_model"),
                metadata == null ? null : json.readValue(metadata, METADATA),
                new AnswerMetrics(
                        rs.getObject("latency_ms", Long.class),
                        rs.getObject("input_tokens", Integer.class),
                        rs.getObject("output_tokens", Integer.class),
                        rs.getBigDecimal("estimated_cost"),
                        rs.getString("cost_currency"),
                        CostStatus.valueOf(rs.getString("cost_status"))),
                Objects.requireNonNull(Timestamps.read(rs, "created_at")),
                Timestamps.read(rs, "completed_at"),
                List.of());
    }

    private static StoredEvidence mapEvidence(ResultSet rs, int rowNum) throws SQLException {
        return new StoredEvidence(
                rs.getObject("id", UUID.class),
                rs.getObject("answer_id", UUID.class),
                rs.getObject("snapshot_id", UUID.class),
                new AnswerEvidence(
                        rs.getObject("chunk_id", UUID.class),
                        rs.getString("citation_label"),
                        rs.getString("relative_path"),
                        strings(rs.getArray("other_paths")),
                        rs.getString("content_hash"),
                        rs.getInt("start_line"),
                        rs.getInt("end_line"),
                        rs.getObject("cited_start_line", Integer.class),
                        rs.getObject("cited_end_line", Integer.class),
                        rs.getString("snippet"),
                        rs.getObject("rank_lexical", Integer.class),
                        rs.getObject("rank_vector", Integer.class),
                        rs.getObject("rank_path", Integer.class),
                        rs.getInt("final_rank"),
                        EvidenceValidation.valueOf(rs.getString("validation_status"))));
    }

    private static List<String> strings(Array array) throws SQLException {
        return List.of((String[]) array.getArray());
    }
}
