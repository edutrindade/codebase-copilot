package com.codebasecopilot.retrieval;

import com.codebasecopilot.codeanalysis.PathTerms;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// Chunks of the files a question names (D-058): files whose path is similar to a path term, by
// strict word similarity, then their chunks in line order.
@Repository
class PathSearch {

    static final double DEFAULT_THRESHOLD = 0.8;

    // pg_trgm's default strict_word_similarity_threshold, used by the <<% prefilter.
    private static final double PREFILTER_THRESHOLD = 0.5;

    // File names without directory and last extension, as typed in a question.
    static final String MATCHING_NAMES =
            """
            SELECT DISTINCT w.name
            FROM unnest(string_to_array(:names, E'\\n')) AS w(name)
            WHERE EXISTS (SELECT 1 FROM snapshot_files f
                          WHERE f.snapshot_id = :snapshotId
                            AND f.content_hash IS NOT NULL
                            AND lower(regexp_replace(f.relative_path, '^.*/|\\.[^.]*$', '', 'g'))
                                = lower(w.name))
            """;

    // Terms never contain whitespace, so they travel as one parameter, one per line. <<% uses
    // the trigram index; the threshold is a parameter instead of the session setting.
    // A chunk shared by several matched files takes the best of them.
    static final String SEARCH =
            """
            WITH matched AS (
                SELECT f.relative_path,
                       max(strict_word_similarity(t.term, f.relative_path)) AS similarity
                FROM unnest(string_to_array(:terms, E'\\n')) AS t(term)
                JOIN snapshot_files f ON t.term <<% f.relative_path
                WHERE f.snapshot_id = :snapshotId
                  AND f.content_hash IS NOT NULL
                  AND strict_word_similarity(t.term, f.relative_path) >= :threshold
                GROUP BY f.relative_path
            ),
            per_chunk AS (
                SELECT DISTINCT ON (c.id) c.id, c.start_line, m.similarity AS score,
                       m.relative_path COLLATE "C" AS matched_path
                FROM matched m
                JOIN snapshots s ON s.id = :snapshotId
                JOIN snapshot_files f ON f.snapshot_id = s.id
                                     AND f.relative_path = m.relative_path
                JOIN chunks c ON c.project_id = f.project_id
                             AND c.content_hash = f.content_hash
                             AND c.chunking_version = s.chunking_version
                ORDER BY c.id, m.similarity DESC, m.relative_path COLLATE "C"
            ),
            ranked AS (
                SELECT * FROM per_chunk
                ORDER BY score DESC, matched_path, start_line, id
                LIMIT :limit
            )
            """
                    + SnapshotChunks.hits("r.score DESC, r.matched_path, r.start_line, r.id");

    private final JdbcClient jdbc;

    PathSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Empty when the question names no file, so the channel then adds nothing to the fusion.
    List<SearchHit> search(UUID snapshotId, String question, double threshold, int limit) {
        if (threshold < PREFILTER_THRESHOLD || threshold > 1) {
            throw new IllegalArgumentException("threshold must be between 0.5 and 1");
        }
        List<String> terms = terms(snapshotId, PathTerms.of(question));
        if (terms.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SEARCH)
                .param("snapshotId", snapshotId)
                .param("terms", String.join("\n", terms))
                .param("threshold", threshold)
                .param("limit", limit)
                .query(SnapshotChunks::hit)
                .list();
    }

    private List<String> terms(UUID snapshotId, PathTerms.Terms found) {
        List<String> terms = new ArrayList<>(found.paths());
        if (!found.names().isEmpty()) {
            terms.addAll(
                    jdbc.sql(MATCHING_NAMES)
                            .param("snapshotId", snapshotId)
                            .param("names", String.join("\n", found.names()))
                            .query(String.class)
                            .list());
        }
        return terms;
    }
}
