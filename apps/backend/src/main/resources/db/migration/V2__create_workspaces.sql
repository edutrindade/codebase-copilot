CREATE TABLE workspaces (
    id         uuid        PRIMARY KEY,
    name       text        NOT NULL
                           CONSTRAINT workspaces_name_length
                           CHECK (char_length(name) BETWEEN 1 AND 100),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX workspaces_created_at_id_idx
    ON workspaces (created_at DESC, id DESC);
