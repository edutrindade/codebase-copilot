CREATE TABLE projects (
    id                   uuid        PRIMARY KEY,
    workspace_id         uuid        NOT NULL
                                     CONSTRAINT projects_workspace_id_fkey
                                     REFERENCES workspaces (id) ON DELETE RESTRICT,
    name                 text        NOT NULL
                                     CONSTRAINT projects_name_length
                                     CHECK (char_length(name) BETWEEN 1 AND 100),
    source_type          text        NOT NULL
                                     CONSTRAINT projects_source_type_valid
                                     CHECK (source_type IN ('LOCAL_FOLDER', 'ZIP')),
    source_root_key      text,
    source_relative_path text,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    CONSTRAINT projects_source_consistency CHECK (
        (source_type = 'LOCAL_FOLDER'
            AND source_root_key IS NOT NULL
            AND source_relative_path IS NOT NULL)
     OR (source_type = 'ZIP'
            AND source_root_key IS NULL
            AND source_relative_path IS NULL)
    )
);

CREATE UNIQUE INDEX projects_local_folder_unique_idx
    ON projects (workspace_id, source_root_key, source_relative_path)
    WHERE source_type = 'LOCAL_FOLDER';

CREATE INDEX projects_workspace_created_at_id_idx
    ON projects (workspace_id, created_at DESC, id DESC);
