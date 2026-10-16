package com.codebasecopilot.retrieval;

import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// Full-text search over the lexical rows of one snapshot's chunks (D-017, D-057). The question
// goes through the tokenizer of the index, and its terms are joined with OR.
@Repository
class LexicalSearch {

    // Weights A=1.0 for identifier terms and B=0.4 for content (the defaults); normalization 1
    // divides by 1 + log of the document length. Ties as in the vector search.
    static final String SEARCH =
            "WITH "
                    + SnapshotChunks.CANDIDATES
                    + """
                    , ranked AS (
                        SELECT k.id, k.first_path, k.start_line,
                               ts_rank_cd(l.tsv, q.query, 1) AS score
                        FROM candidates k
                        JOIN chunk_lexical l ON l.chunk_id = k.id
                                            AND l.lexical_version = :version
                        CROSS JOIN to_tsquery('simple', :query) AS q(query)
                        WHERE l.tsv @@ q.query
                        ORDER BY score DESC, k.first_path, k.start_line, k.id
                        LIMIT :limit
                    )
                    """
                    + SnapshotChunks.hits("r.score DESC, r.first_path, r.start_line, r.id");

    private final JdbcClient jdbc;

    LexicalSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // A question with only stopwords has no terms and finds nothing.
    List<SearchHit> search(UUID snapshotId, String question, int limit) {
        List<String> terms = IdentifierTokenizer.queryTerms(question);
        if (terms.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SEARCH)
                .param("snapshotId", snapshotId)
                .param("version", IdentifierTokenizer.VERSION)
                .param("query", tsquery(terms))
                .param("limit", limit)
                .query(SnapshotChunks::hit)
                .list();
    }

    // Quoting keeps operators out of the query; to_tsquery then lowercases each term like
    // to_tsvector did in the index (D-055).
    static String tsquery(List<String> terms) {
        return terms.stream()
                .map(term -> "'" + term.replace("'", "''") + "'")
                .collect(Collectors.joining(" | "));
    }
}
