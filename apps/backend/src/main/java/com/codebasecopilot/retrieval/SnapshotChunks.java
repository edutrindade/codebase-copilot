package com.codebasecopilot.retrieval;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

// SQL shared by the search channels (D-016, D-039).
final class SnapshotChunks {

    // The "candidates" CTE: one row per chunk of the snapshot :snapshotId, with the first of its
    // paths in byte order. A pruned snapshot has no files left, so nothing matches.
    static final String CANDIDATES =
            """
            candidates AS (
                SELECT c.id, c.chunk_hash, c.start_line,
                       min(f.relative_path COLLATE "C") AS first_path
                FROM snapshots s
                JOIN snapshot_files f ON f.snapshot_id = s.id
                JOIN chunks c ON c.project_id = f.project_id
                             AND c.content_hash = f.content_hash
                             AND c.chunking_version = s.chunking_version
                WHERE s.id = :snapshotId
                  AND f.content_hash IS NOT NULL
                GROUP BY c.id, c.chunk_hash, c.start_line
            )
            """;

    private SnapshotChunks() {}

    // Reads the "ranked" CTE (chunk id and score) in the given order, with every path of the
    // snapshot that has the chunk's content.
    static String hits(String orderBy) {
        return """
                SELECT r.id, c.content_hash, c.start_line, c.end_line, c.content,
                       c.token_count_estimate, r.score,
                       ARRAY(SELECT f.relative_path
                             FROM snapshot_files f
                             WHERE f.snapshot_id = :snapshotId
                               AND f.project_id = c.project_id
                               AND f.content_hash = c.content_hash
                             ORDER BY f.relative_path COLLATE "C") AS paths
                FROM ranked r
                JOIN chunks c ON c.id = r.id
                ORDER BY \
                """
                + orderBy;
    }

    static SearchHit hit(ResultSet rs, int rowNum) throws SQLException {
        Array paths = rs.getArray("paths");
        try {
            return new SearchHit(
                    rs.getObject("id", UUID.class),
                    Arrays.asList((String[]) paths.getArray()),
                    rs.getString("content_hash"),
                    rs.getInt("start_line"),
                    rs.getInt("end_line"),
                    rs.getString("content"),
                    rs.getInt("token_count_estimate"),
                    rs.getDouble("score"));
        } finally {
            paths.free();
        }
    }
}
