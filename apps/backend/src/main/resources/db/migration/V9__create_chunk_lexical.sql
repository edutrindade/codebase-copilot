-- Lexical index per chunk and tokenizer version, path trigrams and the LEXICAL job stage
-- (D-017, D-056). tsv is built with to_tsvector so lexemes keep positions: setweight and
-- ts_rank_cd ignore lexemes without them.
CREATE TABLE chunk_lexical (
    chunk_id         uuid     NOT NULL
                              CONSTRAINT chunk_lexical_chunk_id_fkey
                              REFERENCES chunks (id) ON DELETE CASCADE,
    lexical_version  text     NOT NULL,
    identifier_terms text     NOT NULL,
    tsv              tsvector NOT NULL,
    PRIMARY KEY (chunk_id, lexical_version)
);

CREATE INDEX chunk_lexical_tsv_idx ON chunk_lexical USING gin (tsv);

CREATE INDEX snapshot_files_path_trgm_idx
    ON snapshot_files USING gin (relative_path gin_trgm_ops);

ALTER TABLE snapshots ADD COLUMN lexical_version text;

ALTER TABLE index_jobs
    DROP CONSTRAINT index_jobs_stage_valid,
    ADD CONSTRAINT index_jobs_stage_valid
        CHECK (stage IN ('SCANNING', 'PROCESSING_FILES', 'LEXICAL', 'EMBEDDING', 'FINALIZING'));
