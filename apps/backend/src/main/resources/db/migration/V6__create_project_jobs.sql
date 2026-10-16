-- One row per job of a project, import or index; at most one active job per project (D-041).
CREATE TABLE project_jobs (
    id         uuid        PRIMARY KEY,
    project_id uuid        NOT NULL
                           CONSTRAINT project_jobs_project_id_fkey
                           REFERENCES projects (id) ON DELETE CASCADE,
    kind       text        NOT NULL
                           CONSTRAINT project_jobs_kind_valid
                           CHECK (kind IN ('IMPORT', 'INDEX')),
    active     boolean     NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX project_jobs_active_unique_idx
    ON project_jobs (project_id)
    WHERE active;

-- import_jobs_active_unique_idx already allows at most one active import per project.
INSERT INTO project_jobs (id, project_id, kind, active, created_at)
SELECT id, project_id, 'IMPORT', status IN ('PENDING', 'EXTRACTING'), created_at
FROM import_jobs;

-- NO ACTION (checked at the end of the statement) so the project cascade deletes both tables.
ALTER TABLE import_jobs
    ADD CONSTRAINT import_jobs_project_job_fkey
        FOREIGN KEY (id) REFERENCES project_jobs (id);
