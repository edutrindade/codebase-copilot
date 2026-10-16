-- Optional row: a project without settings uses the defaults (D-036).
CREATE TABLE project_settings (
    project_id             uuid        PRIMARY KEY
                                       CONSTRAINT project_settings_project_id_fkey
                                       REFERENCES projects (id) ON DELETE CASCADE,
    exclude_patterns       text[]      NOT NULL,
    disabled_default_rules text[]      NOT NULL,
    max_file_bytes         bigint,
    max_project_bytes      bigint,
    settings_version       integer     NOT NULL
                                       CONSTRAINT project_settings_version_valid
                                       CHECK (settings_version > 0),
    updated_at             timestamptz NOT NULL
);
