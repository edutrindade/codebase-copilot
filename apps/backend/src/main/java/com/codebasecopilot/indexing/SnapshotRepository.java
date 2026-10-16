package com.codebasecopilot.indexing;

import java.time.Instant;
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

    void markFailed(UUID id, Instant now) {
        jdbc.sql(
                        """
                        UPDATE snapshots SET status = 'FAILED', completed_at = :now
                        WHERE id = :id AND status = 'BUILDING'
                        """)
                .param("id", id)
                .param("now", Timestamps.of(now))
                .update();
    }
}
