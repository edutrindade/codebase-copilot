-- Index jobs cancelled on request, and the snapshot each one was building (D-084).
ALTER TABLE index_jobs ADD COLUMN cancel_requested_at timestamptz;

ALTER TABLE index_jobs
    DROP CONSTRAINT index_jobs_status_valid,
    ADD CONSTRAINT index_jobs_status_valid
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'));

ALTER TABLE index_jobs DROP CONSTRAINT index_jobs_state_consistency;

ALTER TABLE index_jobs ADD CONSTRAINT index_jobs_state_consistency CHECK (
    (status = 'PENDING' AND completed_at IS NULL AND error_code IS NULL)
 OR (status = 'RUNNING' AND completed_at IS NULL AND error_code IS NULL
        AND heartbeat_at IS NOT NULL)
 OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND error_code IS NULL)
 OR (status = 'FAILED' AND completed_at IS NOT NULL AND error_code IS NOT NULL)
 OR (status = 'CANCELLED' AND completed_at IS NOT NULL AND error_code IS NULL)
);

ALTER TABLE snapshots
    DROP CONSTRAINT snapshots_status_valid,
    ADD CONSTRAINT snapshots_status_valid
        CHECK (status IN ('BUILDING', 'READY', 'PARTIAL', 'FAILED', 'CANCELLED'));

ALTER TABLE snapshots DROP CONSTRAINT snapshots_state_consistency;

ALTER TABLE snapshots ADD CONSTRAINT snapshots_state_consistency CHECK (
    (status = 'BUILDING' AND completed_at IS NULL AND pruned_at IS NULL)
 OR (status IN ('READY', 'PARTIAL') AND completed_at IS NOT NULL
        AND file_count IS NOT NULL AND chunk_count IS NOT NULL)
 OR (status IN ('FAILED', 'CANCELLED') AND completed_at IS NOT NULL)
);

