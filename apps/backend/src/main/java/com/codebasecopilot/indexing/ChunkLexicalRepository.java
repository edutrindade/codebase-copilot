package com.codebasecopilot.indexing;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// Lexical rows belong to a chunk and a tokenizer version, so versions coexist and the chunk
// cascade removes them (D-056).
@Repository
class ChunkLexicalRepository {

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate batch;

    ChunkLexicalRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    record NewLexical(UUID chunkId, String identifierTerms) {}

    List<UUID> missingChunkIds(UUID snapshotId, String lexicalVersion) {
        return jdbc.sql(
                        """
                        SELECT DISTINCT c.id
                        FROM snapshots s
                        JOIN snapshot_files f ON f.snapshot_id = s.id
                        JOIN chunks c ON c.project_id = f.project_id
                                     AND c.content_hash = f.content_hash
                                     AND c.chunking_version = s.chunking_version
                        WHERE s.id = :snapshotId
                          AND f.content_hash IS NOT NULL
                          AND NOT EXISTS (SELECT 1 FROM chunk_lexical l
                                          WHERE l.chunk_id = c.id
                                            AND l.lexical_version = :version)
                        ORDER BY c.id
                        """)
                .param("snapshotId", snapshotId)
                .param("version", lexicalVersion)
                .query(UUID.class)
                .list();
    }

    Map<UUID, String> chunkTexts(Collection<UUID> chunkIds) {
        if (chunkIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> texts = new HashMap<>();
        jdbc.sql("SELECT id, content FROM chunks WHERE id IN (:ids)")
                .param("ids", chunkIds)
                .query(
                        rs -> {
                            texts.put(rs.getObject("id", UUID.class), rs.getString("content"));
                        });
        return texts;
    }

    // The content side of tsv comes from the chunk row, so its text is not sent back.
    void insert(String lexicalVersion, List<NewLexical> rows) {
        if (rows.isEmpty()) {
            return;
        }
        batch.batchUpdate(
                """
                INSERT INTO chunk_lexical (chunk_id, lexical_version, identifier_terms, tsv)
                SELECT c.id, :version, :terms,
                       setweight(to_tsvector('simple', :terms), 'A')
                           || setweight(to_tsvector('simple', c.content), 'B')
                FROM chunks c
                WHERE c.id = :chunkId
                ON CONFLICT DO NOTHING
                """,
                rows.stream()
                        .map(
                                row ->
                                        new MapSqlParameterSource()
                                                .addValue("chunkId", row.chunkId())
                                                .addValue("version", lexicalVersion)
                                                .addValue("terms", row.identifierTerms()))
                        .toArray(SqlParameterSource[]::new));
    }
}
