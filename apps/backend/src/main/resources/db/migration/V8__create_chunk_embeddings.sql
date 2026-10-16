-- Embeddings cached by chunk hash and model, plus embedding metadata on snapshots (D-048).
-- The vector column has no fixed dimension so a model change needs no migration; the row
-- carries its dimension and every query filters by provider, model and dimension.
CREATE TABLE chunk_embeddings (
    chunk_hash text        NOT NULL,
    provider   text        NOT NULL,
    model      text        NOT NULL,
    dimensions integer     NOT NULL
                           CONSTRAINT chunk_embeddings_dimensions_valid
                           CHECK (dimensions BETWEEN 1 AND 2000),
    embedding  vector      NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (chunk_hash, provider, model, dimensions),
    CONSTRAINT chunk_embeddings_dimensions_match
        CHECK (vector_dims(embedding) = dimensions)
);

ALTER TABLE snapshots
    ADD COLUMN embedding_provider   text,
    ADD COLUMN embedding_model      text,
    ADD COLUMN embedding_dimensions integer,
    ADD COLUMN embedding_tokens     bigint,
    ADD CONSTRAINT snapshots_embedding_consistency CHECK (
        (embedding_provider IS NULL AND embedding_model IS NULL
            AND embedding_dimensions IS NULL)
     OR (embedding_provider IS NOT NULL AND embedding_model IS NOT NULL
            AND embedding_dimensions IS NOT NULL)
    );

ALTER TABLE index_jobs
    DROP CONSTRAINT index_jobs_stage_valid,
    ADD CONSTRAINT index_jobs_stage_valid
        CHECK (stage IN ('SCANNING', 'PROCESSING_FILES', 'EMBEDDING', 'FINALIZING'));
