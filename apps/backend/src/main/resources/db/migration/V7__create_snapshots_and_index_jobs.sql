-- Snapshots, index jobs, file contents and chunks (D-039, D-040, D-044).
CREATE TABLE snapshots (
    id                     uuid        PRIMARY KEY,
    project_id             uuid        NOT NULL
                                       CONSTRAINT snapshots_project_id_fkey
                                       REFERENCES projects (id) ON DELETE CASCADE,
    status                 text        NOT NULL
                                       CONSTRAINT snapshots_status_valid
                                       CHECK (status IN ('BUILDING', 'READY', 'PARTIAL', 'FAILED')),
    source_import_job_id   uuid,
    settings_version       integer     NOT NULL,
    chunking_version       text        NOT NULL,
    inventory              jsonb,
    file_count             integer,
    indexed_file_count     integer,
    unsupported_file_count integer,
    failed_file_count      integer,
    chunk_count            integer,
    total_bytes            bigint,
    added_count            integer,
    modified_count         integer,
    removed_count          integer,
    unchanged_count        integer,
    created_at             timestamptz NOT NULL,
    completed_at           timestamptz,
    pruned_at              timestamptz,
    CONSTRAINT snapshots_project_id_id_key UNIQUE (project_id, id),
    CONSTRAINT snapshots_source_import_fkey
        FOREIGN KEY (project_id, source_import_job_id)
        REFERENCES import_jobs (project_id, id),
    CONSTRAINT snapshots_state_consistency CHECK (
        (status = 'BUILDING' AND completed_at IS NULL AND pruned_at IS NULL)
     OR (status IN ('READY', 'PARTIAL') AND completed_at IS NOT NULL
            AND file_count IS NOT NULL AND chunk_count IS NOT NULL)
     OR (status = 'FAILED' AND completed_at IS NOT NULL)
    )
);

CREATE INDEX snapshots_project_created_at_id_idx
    ON snapshots (project_id, created_at DESC, id DESC);

CREATE TABLE index_jobs (
    id                 uuid        PRIMARY KEY
                                   CONSTRAINT index_jobs_project_job_fkey
                                   REFERENCES project_jobs (id),
    project_id         uuid        NOT NULL
                                   CONSTRAINT index_jobs_project_id_fkey
                                   REFERENCES projects (id) ON DELETE CASCADE,
    snapshot_id        uuid        NOT NULL
                                   CONSTRAINT index_jobs_snapshot_id_key UNIQUE,
    status             text        NOT NULL
                                   CONSTRAINT index_jobs_status_valid
                                   CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    stage              text
                                   CONSTRAINT index_jobs_stage_valid
                                   CHECK (stage IN ('SCANNING', 'PROCESSING_FILES', 'FINALIZING')),
    progress_total     integer,
    progress_completed integer,
    attempt_count      integer     NOT NULL DEFAULT 0,
    heartbeat_at       timestamptz,
    error_code         text,
    created_at         timestamptz NOT NULL,
    started_at         timestamptz,
    completed_at       timestamptz,
    CONSTRAINT index_jobs_snapshot_fkey
        FOREIGN KEY (project_id, snapshot_id)
        REFERENCES snapshots (project_id, id) ON DELETE CASCADE,
    CONSTRAINT index_jobs_state_consistency CHECK (
        (status = 'PENDING' AND completed_at IS NULL AND error_code IS NULL)
     OR (status = 'RUNNING' AND completed_at IS NULL AND error_code IS NULL
            AND heartbeat_at IS NOT NULL)
     OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND error_code IS NULL)
     OR (status = 'FAILED' AND completed_at IS NOT NULL AND error_code IS NOT NULL)
    )
);

CREATE UNIQUE INDEX index_jobs_active_unique_idx
    ON index_jobs (project_id)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX index_jobs_claim_idx
    ON index_jobs (created_at)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX index_jobs_project_created_at_id_idx
    ON index_jobs (project_id, created_at DESC, id DESC);

CREATE TABLE file_contents (
    project_id   uuid        NOT NULL
                             CONSTRAINT file_contents_project_id_fkey
                             REFERENCES projects (id) ON DELETE CASCADE,
    content_hash text        NOT NULL,
    content      text        NOT NULL,
    size_bytes   bigint      NOT NULL,
    line_count   integer     NOT NULL,
    created_at   timestamptz NOT NULL,
    PRIMARY KEY (project_id, content_hash)
);

-- NO ACTION (checked at the end of the statement) so the project cascade deletes both tables.
CREATE TABLE snapshot_files (
    snapshot_id   uuid   NOT NULL
                         CONSTRAINT snapshot_files_snapshot_id_fkey
                         REFERENCES snapshots (id) ON DELETE CASCADE,
    project_id    uuid   NOT NULL,
    relative_path text   NOT NULL,
    status        text   NOT NULL
                         CONSTRAINT snapshot_files_status_valid
                         CHECK (status IN ('INDEXED', 'UNSUPPORTED', 'FAILED')),
    reason        text,
    content_hash  text,
    language      text   NOT NULL,
    size_bytes    bigint NOT NULL,
    PRIMARY KEY (snapshot_id, relative_path),
    CONSTRAINT snapshot_files_content_fkey
        FOREIGN KEY (project_id, content_hash)
        REFERENCES file_contents (project_id, content_hash),
    CONSTRAINT snapshot_files_state_consistency CHECK (
        (status = 'INDEXED' AND content_hash IS NOT NULL AND reason IS NULL)
     OR (status = 'UNSUPPORTED'
            AND reason IS NOT NULL
            AND reason IN ('BINARY', 'INVALID_ENCODING', 'LINE_TOO_LONG')
            AND content_hash IS NULL)
     OR (status = 'FAILED'
            AND reason IS NOT NULL
            AND reason IN ('READ_ERROR', 'CHANGED_DURING_READ')
            AND content_hash IS NULL)
    )
);

CREATE INDEX snapshot_files_content_idx
    ON snapshot_files (project_id, content_hash);

CREATE TABLE chunks (
    id                   uuid    PRIMARY KEY,
    project_id           uuid    NOT NULL,
    content_hash         text    NOT NULL,
    chunking_version     text    NOT NULL,
    ordinal              integer NOT NULL,
    start_line           integer NOT NULL,
    end_line             integer NOT NULL,
    content              text    NOT NULL,
    chunk_hash           text    NOT NULL,
    token_count_estimate integer NOT NULL,
    CONSTRAINT chunks_natural_key
        UNIQUE (project_id, content_hash, chunking_version, ordinal),
    CONSTRAINT chunks_content_fkey
        FOREIGN KEY (project_id, content_hash)
        REFERENCES file_contents (project_id, content_hash) ON DELETE CASCADE,
    CONSTRAINT chunks_lines_valid CHECK (start_line >= 1 AND end_line >= start_line)
);

CREATE INDEX chunks_chunk_hash_idx ON chunks (chunk_hash);

ALTER TABLE projects
    ADD COLUMN active_snapshot_id uuid,
    ADD CONSTRAINT projects_active_snapshot_fkey
        FOREIGN KEY (id, active_snapshot_id)
        REFERENCES snapshots (project_id, id)
        ON DELETE SET NULL (active_snapshot_id);
