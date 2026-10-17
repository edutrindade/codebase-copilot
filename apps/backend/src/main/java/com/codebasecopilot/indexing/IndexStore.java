package com.codebasecopilot.indexing;

import com.codebasecopilot.indexing.content.LineChunker;
import com.codebasecopilot.shared.IdGenerator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// Contents and chunks are immutable and keyed by their natural keys, so every insert is
// idempotent (D-039).
@Repository
class IndexStore {

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate batch;
    private final IdGenerator idGenerator;
    private final LineChunker.Parameters chunking;

    IndexStore(
            JdbcClient jdbc,
            NamedParameterJdbcTemplate batch,
            IdGenerator idGenerator,
            LineChunker.Parameters chunking) {
        this.jdbc = jdbc;
        this.batch = batch;
        this.idGenerator = idGenerator;
        this.chunking = chunking;
    }

    record NewContent(
            String contentHash,
            String content,
            long sizeBytes,
            int lineCount,
            List<LineChunker.Chunk> chunks) {}

    record FileRow(
            String relativePath,
            FileStatus status,
            @Nullable String reason,
            @Nullable String contentHash,
            String language,
            long sizeBytes) {}

    // Contents already stored with the chunks of this version. A blank content has no chunks,
    // so its stored row is enough.
    Set<String> chunkedHashes(UUID projectId, Collection<String> hashes, String chunkingVersion) {
        if (hashes.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(
                jdbc.sql(
                                """
                                SELECT fc.content_hash
                                FROM file_contents fc
                                WHERE fc.project_id = :projectId
                                  AND fc.content_hash IN (:hashes)
                                  AND (EXISTS (SELECT 1 FROM chunks c
                                               WHERE c.project_id = fc.project_id
                                                 AND c.content_hash = fc.content_hash
                                                 AND c.chunking_version = :version)
                                       OR btrim(fc.content, E' \\t\\n\\r\\f\\x0B') = '')
                                """)
                        .param("projectId", projectId)
                        .param("hashes", hashes)
                        .param("version", chunkingVersion)
                        .query(String.class)
                        .list());
    }

    void insertContents(UUID projectId, List<NewContent> contents, Instant now) {
        if (contents.isEmpty()) {
            return;
        }
        OffsetDateTime createdAt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        List<SqlParameterSource> contentRows = new ArrayList<>();
        List<SqlParameterSource> chunkRows = new ArrayList<>();
        for (NewContent content : contents) {
            contentRows.add(
                    new MapSqlParameterSource()
                            .addValue("projectId", projectId)
                            .addValue("hash", content.contentHash())
                            .addValue("content", content.content())
                            .addValue("sizeBytes", content.sizeBytes())
                            .addValue("lineCount", content.lineCount())
                            .addValue("createdAt", createdAt));
            for (LineChunker.Chunk chunk : content.chunks()) {
                chunkRows.add(
                        new MapSqlParameterSource()
                                .addValue("id", idGenerator.newId())
                                .addValue("projectId", projectId)
                                .addValue("hash", content.contentHash())
                                .addValue("version", chunking.version())
                                .addValue("ordinal", chunk.ordinal())
                                .addValue("startLine", chunk.startLine())
                                .addValue("endLine", chunk.endLine())
                                .addValue("content", chunk.content())
                                .addValue("chunkHash", chunk.chunkHash())
                                .addValue("tokens", chunk.tokenCountEstimate()));
            }
        }
        batch.batchUpdate(
                """
                INSERT INTO file_contents (project_id, content_hash, content, size_bytes,
                                           line_count, created_at)
                VALUES (:projectId, :hash, :content, :sizeBytes, :lineCount, :createdAt)
                ON CONFLICT DO NOTHING
                """,
                contentRows.toArray(SqlParameterSource[]::new));
        if (!chunkRows.isEmpty()) {
            batch.batchUpdate(
                    """
                    INSERT INTO chunks (id, project_id, content_hash, chunking_version, ordinal,
                                        start_line, end_line, content, chunk_hash,
                                        token_count_estimate)
                    VALUES (:id, :projectId, :hash, :version, :ordinal, :startLine, :endLine,
                            :content, :chunkHash, :tokens)
                    ON CONFLICT ON CONSTRAINT chunks_natural_key DO NOTHING
                    """,
                    chunkRows.toArray(SqlParameterSource[]::new));
        }
    }

    void insertFiles(UUID snapshotId, UUID projectId, List<FileRow> files) {
        if (files.isEmpty()) {
            return;
        }
        batch.batchUpdate(
                """
                INSERT INTO snapshot_files (snapshot_id, project_id, relative_path, status,
                                            reason, content_hash, language, size_bytes)
                VALUES (:snapshotId, :projectId, :path, :status, :reason, :hash, :language,
                        :sizeBytes)
                ON CONFLICT DO NOTHING
                """,
                files.stream()
                        .map(
                                f ->
                                        new MapSqlParameterSource()
                                                .addValue("snapshotId", snapshotId)
                                                .addValue("projectId", projectId)
                                                .addValue("path", f.relativePath())
                                                .addValue("status", f.status().name())
                                                .addValue("reason", f.reason())
                                                .addValue("hash", f.contentHash())
                                                .addValue("language", f.language())
                                                .addValue("sizeBytes", f.sizeBytes()))
                        .toArray(SqlParameterSource[]::new));
    }

    void deleteFiles(UUID snapshotId) {
        jdbc.sql("DELETE FROM snapshot_files WHERE snapshot_id = :id")
                .param("id", snapshotId)
                .update();
    }
}
