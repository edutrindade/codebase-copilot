package com.codebasecopilot.indexing;

import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SnapshotRepository {

    private final JdbcClient jdbc;

    SnapshotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Counts(
            int files,
            int indexedFiles,
            int unsupportedFiles,
            int failedFiles,
            int chunks,
            long bytes) {}

    record Changes(int added, int modified, int removed, int unchanged) {}

    record Embedding(ModelInfo model, @Nullable Long tokens) {}

    record FileItem(
            String relativePath,
            FileStatus status,
            @Nullable String reason,
            String language,
            long sizeBytes,
            @Nullable Integer lineCount,
            @Nullable String contentHash,
            int chunkCount) {}

    private static final String COLUMNS =
            """
            id, project_id, status, source_import_job_id, settings_version, chunking_version,
            inventory::text AS inventory, file_count, indexed_file_count, unsupported_file_count,
            failed_file_count, chunk_count, total_bytes, added_count, modified_count,
            removed_count, unchanged_count, embedding_provider, embedding_model,
            embedding_dimensions, embedding_tokens, lexical_version, created_at, completed_at,
            pruned_at
            """;

    void insertBuilding(
            UUID id,
            UUID projectId,
            @Nullable UUID sourceImportJobId,
            int settingsVersion,
            String chunkingVersion,
            Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO snapshots (id, project_id, status, source_import_job_id,
                                               settings_version, chunking_version, created_at)
                        VALUES (:id, :projectId, 'BUILDING', :importId, :settingsVersion,
                                :chunkingVersion, :now)
                        """)
                .param("id", id)
                .param("projectId", projectId)
                .param("importId", sourceImportJobId)
                .param("settingsVersion", settingsVersion)
                .param("chunkingVersion", chunkingVersion)
                .param("now", Timestamps.of(now))
                .update();
    }

    void recordScan(UUID id, int settingsVersion, String inventoryJson) {
        jdbc.sql(
                        """
                        UPDATE snapshots
                        SET settings_version = :settingsVersion,
                            inventory = CAST(:inventory AS jsonb)
                        WHERE id = :id AND status = 'BUILDING'
                        """)
                .param("id", id)
                .param("settingsVersion", settingsVersion)
                .param("inventory", inventoryJson)
                .update();
    }

    void markFinished(
            UUID id, SnapshotStatus status, Counts counts, @Nullable Changes changes, Instant now) {
        jdbc.sql(
                        """
                        UPDATE snapshots
                        SET status = :status, completed_at = :now, file_count = :files,
                            indexed_file_count = :indexed, unsupported_file_count = :unsupported,
                            failed_file_count = :failed, chunk_count = :chunks,
                            total_bytes = :bytes, added_count = :added,
                            modified_count = :modified, removed_count = :removed,
                            unchanged_count = :unchanged
                        WHERE id = :id AND status = 'BUILDING'
                        """)
                .param("id", id)
                .param("status", status.name())
                .param("now", Timestamps.of(now))
                .param("files", counts.files())
                .param("indexed", counts.indexedFiles())
                .param("unsupported", counts.unsupportedFiles())
                .param("failed", counts.failedFiles())
                .param("chunks", counts.chunks())
                .param("bytes", counts.bytes())
                .param("added", changes == null ? null : changes.added())
                .param("modified", changes == null ? null : changes.modified())
                .param("removed", changes == null ? null : changes.removed())
                .param("unchanged", changes == null ? null : changes.unchanged())
                .update();
    }

    void recordEmbedding(UUID id, ModelInfo model, @Nullable Long tokens) {
        jdbc.sql(
                        """
                        UPDATE snapshots
                        SET embedding_provider = :provider, embedding_model = :model,
                            embedding_dimensions = :dimensions, embedding_tokens = :tokens
                        WHERE id = :id AND status = 'BUILDING'
                        """)
                .param("id", id)
                .param("provider", model.provider())
                .param("model", model.model())
                .param("dimensions", model.dimensions())
                .param("tokens", tokens)
                .update();
    }

    void recordLexical(UUID id, String lexicalVersion) {
        jdbc.sql(
                        """
                        UPDATE snapshots SET lexical_version = :version
                        WHERE id = :id AND status = 'BUILDING'
                        """)
                .param("id", id)
                .param("version", lexicalVersion)
                .update();
    }

    void markFailed(UUID id, Instant now) {
        markEnded(id, SnapshotStatus.FAILED, now);
    }

    void markCancelled(UUID id, Instant now) {
        markEnded(id, SnapshotStatus.CANCELLED, now);
    }

    private void markEnded(UUID id, SnapshotStatus status, Instant now) {
        jdbc.sql(
                        """
                        UPDATE snapshots SET status = :status, completed_at = :now
                        WHERE id = :id AND status = 'BUILDING'
                        """)
                .param("id", id)
                .param("status", status.name())
                .param("now", Timestamps.of(now))
                .update();
    }

    Optional<Snapshot> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM snapshots WHERE id = :id")
                .param("id", id)
                .query(SnapshotRepository::map)
                .optional();
    }

    // Without the inventory, which only the snapshot detail returns.
    List<Snapshot> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS.replace(
                                        "inventory::text AS inventory", "NULL AS inventory")
                                + " FROM snapshots WHERE id IN (:ids)")
                .param("ids", ids)
                .query(SnapshotRepository::map)
                .list();
    }

    List<Snapshot> findPageByProject(UUID projectId, PageRequestParams params) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + """
                                FROM snapshots
                                WHERE project_id = :projectId
                                ORDER BY created_at DESC, id DESC
                                LIMIT :limit OFFSET :offset
                                """)
                .param("projectId", projectId)
                .param("limit", params.size())
                .param("offset", params.offset())
                .query(SnapshotRepository::map)
                .list();
    }

    long countByProject(UUID projectId) {
        return jdbc.sql("SELECT count(*) FROM snapshots WHERE project_id = :projectId")
                .param("projectId", projectId)
                .query(Long.class)
                .single();
    }

    // Chunks are counted once per content, even when several paths share it (D-039).
    Counts count(UUID snapshotId, UUID projectId, String chunkingVersion) {
        Counts files =
                jdbc.sql(
                                """
                                SELECT count(*) AS files,
                                       count(*) FILTER (WHERE status = 'INDEXED') AS indexed,
                                       count(*) FILTER (WHERE status = 'UNSUPPORTED')
                                           AS unsupported,
                                       count(*) FILTER (WHERE status = 'FAILED') AS failed,
                                       coalesce(sum(size_bytes) FILTER (WHERE status = 'INDEXED'),
                                                0) AS bytes
                                FROM snapshot_files
                                WHERE snapshot_id = :id
                                """)
                        .param("id", snapshotId)
                        .query(
                                (rs, rowNum) ->
                                        new Counts(
                                                rs.getInt("files"),
                                                rs.getInt("indexed"),
                                                rs.getInt("unsupported"),
                                                rs.getInt("failed"),
                                                0,
                                                rs.getLong("bytes")))
                        .single();
        int chunks =
                jdbc.sql(
                                """
                                SELECT count(*)
                                FROM chunks c
                                WHERE c.project_id = :projectId
                                  AND c.chunking_version = :version
                                  AND c.content_hash IN (SELECT content_hash FROM snapshot_files
                                                         WHERE snapshot_id = :id
                                                           AND content_hash IS NOT NULL)
                                """)
                        .param("id", snapshotId)
                        .param("projectId", projectId)
                        .param("version", chunkingVersion)
                        .query(Integer.class)
                        .single();
        return new Counts(
                files.files(),
                files.indexedFiles(),
                files.unsupportedFiles(),
                files.failedFiles(),
                chunks,
                files.bytes());
    }

    // Compares the INDEXED files of both snapshots by path and content hash (D-044).
    Changes changes(UUID previousId, UUID snapshotId) {
        return jdbc.sql(
                        """
                        SELECT
                            count(*) FILTER (WHERE p.relative_path IS NULL) AS added,
                            count(*) FILTER (WHERE p.content_hash <> n.content_hash) AS modified,
                            count(*) FILTER (WHERE p.content_hash = n.content_hash) AS unchanged,
                            (SELECT count(*)
                             FROM snapshot_files o
                             WHERE o.snapshot_id = :previous AND o.status = 'INDEXED'
                               AND NOT EXISTS (SELECT 1 FROM snapshot_files x
                                               WHERE x.snapshot_id = :id
                                                 AND x.relative_path = o.relative_path
                                                 AND x.status = 'INDEXED')) AS removed
                        FROM snapshot_files n
                        LEFT JOIN snapshot_files p
                               ON p.snapshot_id = :previous
                              AND p.relative_path = n.relative_path
                              AND p.status = 'INDEXED'
                        WHERE n.snapshot_id = :id AND n.status = 'INDEXED'
                        """)
                .param("previous", previousId)
                .param("id", snapshotId)
                .query(
                        (rs, rowNum) ->
                                new Changes(
                                        rs.getInt("added"),
                                        rs.getInt("modified"),
                                        rs.getInt("removed"),
                                        rs.getInt("unchanged")))
                .single();
    }

    // Keeps the active snapshot and the most recent usable ones; the rows of pruned snapshots
    // stay as history, without files (D-044).
    void prune(UUID projectId, UUID activeId, int retained, Instant now) {
        jdbc.sql(
                        """
                        UPDATE snapshots
                        SET pruned_at = :now
                        WHERE project_id = :projectId
                          AND status IN ('READY', 'PARTIAL')
                          AND pruned_at IS NULL
                          AND id <> :activeId
                          AND id NOT IN (SELECT id FROM snapshots
                                         WHERE project_id = :projectId
                                           AND status IN ('READY', 'PARTIAL')
                                           AND pruned_at IS NULL
                                           AND id <> :activeId
                                         ORDER BY created_at DESC, id DESC
                                         LIMIT :retained)
                        """)
                .param("projectId", projectId)
                .param("activeId", activeId)
                .param("retained", retained)
                .param("now", Timestamps.of(now))
                .update();
        jdbc.sql(
                        """
                        DELETE FROM snapshot_files
                        WHERE snapshot_id IN (SELECT id FROM snapshots
                                              WHERE project_id = :projectId
                                                AND pruned_at IS NOT NULL)
                        """)
                .param("projectId", projectId)
                .update();
    }

    // Chunks of a (content, chunking version) and contents that no remaining snapshot uses
    // (D-039). Only the job that holds the project runs it, so no other job is writing.
    void removeUnreferencedContent(UUID projectId) {
        jdbc.sql(
                        """
                        DELETE FROM chunks c
                        WHERE c.project_id = :projectId
                          AND NOT EXISTS (SELECT 1
                                          FROM snapshot_files f
                                          JOIN snapshots s ON s.id = f.snapshot_id
                                          WHERE f.project_id = c.project_id
                                            AND f.content_hash = c.content_hash
                                            AND s.chunking_version = c.chunking_version)
                        """)
                .param("projectId", projectId)
                .update();
        jdbc.sql(
                        """
                        DELETE FROM file_contents fc
                        WHERE fc.project_id = :projectId
                          AND NOT EXISTS (SELECT 1 FROM snapshot_files f
                                          WHERE f.project_id = fc.project_id
                                            AND f.content_hash = fc.content_hash)
                        """)
                .param("projectId", projectId)
                .update();
    }

    List<FileItem> findFiles(
            Snapshot snapshot,
            @Nullable FileStatus status,
            String pathPrefix,
            PageRequestParams params) {
        return jdbc.sql(
                        """
                        SELECT f.relative_path, f.status, f.reason, f.language, f.size_bytes,
                               fc.line_count, f.content_hash,
                               (SELECT count(*) FROM chunks c
                                WHERE c.project_id = f.project_id
                                  AND c.content_hash = f.content_hash
                                  AND c.chunking_version = :version) AS chunk_count
                        FROM snapshot_files f
                        LEFT JOIN file_contents fc
                               ON fc.project_id = f.project_id
                              AND fc.content_hash = f.content_hash
                        WHERE f.snapshot_id = :id
                        """
                                + FILE_FILTERS
                                + """
                                ORDER BY f.relative_path COLLATE "C"
                                LIMIT :limit OFFSET :offset
                                """)
                .param("id", snapshot.id())
                .param("version", snapshot.chunkingVersion())
                .param("status", status == null ? null : status.name())
                .param("prefix", pathPrefix)
                .param("limit", params.size())
                .param("offset", params.offset())
                .query(
                        (rs, rowNum) ->
                                new FileItem(
                                        rs.getString("relative_path"),
                                        FileStatus.valueOf(rs.getString("status")),
                                        rs.getString("reason"),
                                        rs.getString("language"),
                                        rs.getLong("size_bytes"),
                                        rs.getObject("line_count", Integer.class),
                                        rs.getString("content_hash"),
                                        rs.getInt("chunk_count")))
                .list();
    }

    record FileContent(
            FileStatus status,
            String language,
            long sizeBytes,
            @Nullable String contentHash,
            @Nullable Integer lineCount,
            @Nullable String content) {}

    Optional<FileContent> findFileContent(UUID snapshotId, String relativePath) {
        return jdbc.sql(
                        """
                        SELECT f.status, f.language, f.size_bytes, f.content_hash,
                               fc.line_count, fc.content
                        FROM snapshot_files f
                        LEFT JOIN file_contents fc
                               ON fc.project_id = f.project_id
                              AND fc.content_hash = f.content_hash
                        WHERE f.snapshot_id = :id AND f.relative_path = :path
                        """)
                .param("id", snapshotId)
                .param("path", relativePath)
                .query(
                        (rs, rowNum) ->
                                new FileContent(
                                        FileStatus.valueOf(rs.getString("status")),
                                        rs.getString("language"),
                                        rs.getLong("size_bytes"),
                                        rs.getString("content_hash"),
                                        rs.getObject("line_count", Integer.class),
                                        rs.getString("content")))
                .optional();
    }

    long countFiles(UUID snapshotId, @Nullable FileStatus status, String pathPrefix) {
        return jdbc.sql(
                        "SELECT count(*) FROM snapshot_files f WHERE f.snapshot_id = :id"
                                + FILE_FILTERS)
                .param("id", snapshotId)
                .param("status", status == null ? null : status.name())
                .param("prefix", pathPrefix)
                .query(Long.class)
                .single();
    }

    // An empty prefix matches every file; otherwise the prefix is a file or a directory.
    private static final String FILE_FILTERS =
            """
             AND (CAST(:status AS text) IS NULL OR f.status = :status)
             AND (:prefix = '' OR f.relative_path = :prefix
                  OR starts_with(f.relative_path, :prefix || '/'))
            """;

    private static Snapshot map(ResultSet rs, int rowNum) throws SQLException {
        Integer files = rs.getObject("file_count", Integer.class);
        Integer added = rs.getObject("added_count", Integer.class);
        return new Snapshot(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                SnapshotStatus.valueOf(rs.getString("status")),
                rs.getObject("source_import_job_id", UUID.class),
                rs.getInt("settings_version"),
                rs.getString("chunking_version"),
                rs.getString("inventory"),
                files == null
                        ? null
                        : new Counts(
                                files,
                                rs.getInt("indexed_file_count"),
                                rs.getInt("unsupported_file_count"),
                                rs.getInt("failed_file_count"),
                                rs.getInt("chunk_count"),
                                rs.getLong("total_bytes")),
                added == null
                        ? null
                        : new Changes(
                                added,
                                rs.getInt("modified_count"),
                                rs.getInt("removed_count"),
                                rs.getInt("unchanged_count")),
                embedding(rs),
                rs.getString("lexical_version"),
                Objects.requireNonNull(Timestamps.read(rs, "created_at")),
                Timestamps.read(rs, "completed_at"),
                Timestamps.read(rs, "pruned_at"));
    }

    // snapshots_embedding_consistency keeps the three model columns all null or all set.
    private static @Nullable Embedding embedding(ResultSet rs) throws SQLException {
        String provider = rs.getString("embedding_provider");
        if (provider == null) {
            return null;
        }
        return new Embedding(
                new ModelInfo(
                        provider,
                        rs.getString("embedding_model"),
                        rs.getInt("embedding_dimensions")),
                rs.getObject("embedding_tokens", Long.class));
    }
}
