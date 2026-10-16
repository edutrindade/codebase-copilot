CREATE TABLE import_jobs (
    id                 uuid        PRIMARY KEY,
    project_id         uuid        NOT NULL
                                   CONSTRAINT import_jobs_project_id_fkey
                                   REFERENCES projects (id) ON DELETE CASCADE,
    status             text        NOT NULL
                                   CONSTRAINT import_jobs_status_valid
                                   CHECK (status IN ('PENDING', 'EXTRACTING', 'COMPLETED',
                                                     'REJECTED', 'FAILED')),
    compressed_bytes   bigint      NOT NULL
                                   CONSTRAINT import_jobs_compressed_bytes_valid
                                   CHECK (compressed_bytes > 0),
    uncompressed_bytes bigint,
    entry_count        integer,
    error_code         text,
    created_at         timestamptz NOT NULL,
    completed_at       timestamptz,
    CONSTRAINT import_jobs_project_id_id_key UNIQUE (project_id, id),
    CONSTRAINT import_jobs_state_consistency CHECK (
        (status IN ('PENDING', 'EXTRACTING')
            AND completed_at IS NULL AND error_code IS NULL)
     OR (status = 'COMPLETED'
            AND completed_at IS NOT NULL AND error_code IS NULL
            AND uncompressed_bytes IS NOT NULL AND entry_count IS NOT NULL)
     OR (status IN ('REJECTED', 'FAILED')
            AND completed_at IS NOT NULL AND error_code IS NOT NULL)
    )
);

-- At most one import in progress per project (409 PROJECT_BUSY, D-028).
CREATE UNIQUE INDEX import_jobs_active_unique_idx
    ON import_jobs (project_id)
    WHERE status IN ('PENDING', 'EXTRACTING');

CREATE INDEX import_jobs_project_created_at_id_idx
    ON import_jobs (project_id, created_at DESC, id DESC);

-- The composite key ties the current import to its own project; SET NULL clears only the
-- import column, so the cascade from projects to import_jobs works despite the cycle.
ALTER TABLE projects
    ADD COLUMN current_import_job_id uuid,
    ADD CONSTRAINT projects_current_import_fkey
        FOREIGN KEY (id, current_import_job_id)
        REFERENCES import_jobs (project_id, id)
        ON DELETE SET NULL (current_import_job_id),
    ADD CONSTRAINT projects_current_import_zip_only
        CHECK (current_import_job_id IS NULL OR source_type = 'ZIP');
