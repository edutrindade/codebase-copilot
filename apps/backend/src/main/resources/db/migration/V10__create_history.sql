-- Conversations, questions, answers and the evidence copied at answer time (D-046, D-065).
-- Snapshots and chunks are referenced without FK, so pruning and cleanup keep the history;
-- removing the project removes it all (D-021).
CREATE TABLE conversations (
    id         uuid        PRIMARY KEY,
    project_id uuid        NOT NULL
                           CONSTRAINT conversations_project_id_fkey
                           REFERENCES projects (id) ON DELETE CASCADE,
    title      text        NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX conversations_project_updated_at_id_idx
    ON conversations (project_id, updated_at DESC, id DESC);

CREATE TABLE messages (
    id              uuid        PRIMARY KEY,
    conversation_id uuid        NOT NULL
                                CONSTRAINT messages_conversation_id_fkey
                                REFERENCES conversations (id) ON DELETE CASCADE,
    role            text        NOT NULL
                                CONSTRAINT messages_role_valid
                                CHECK (role IN ('USER', 'ASSISTANT')),
    content         text        NOT NULL,
    created_at      timestamptz NOT NULL
);

CREATE INDEX messages_conversation_created_at_id_idx
    ON messages (conversation_id, created_at, id);

CREATE TABLE answers (
    id                     uuid          PRIMARY KEY,
    question_message_id    uuid          NOT NULL
                                         CONSTRAINT answers_question_message_id_key UNIQUE
                                         CONSTRAINT answers_question_message_id_fkey
                                         REFERENCES messages (id) ON DELETE CASCADE,
    answer_message_id      uuid          CONSTRAINT answers_answer_message_id_key UNIQUE
                                         CONSTRAINT answers_answer_message_id_fkey
                                         REFERENCES messages (id) ON DELETE CASCADE,
    snapshot_id            uuid          NOT NULL,
    status                 text          NOT NULL
                                         CONSTRAINT answers_status_valid
                                         CHECK (status IN ('PENDING', 'ANSWERED', 'PARTIAL',
                                                           'ABSTAINED', 'FAILED')),
    retrieval_mode         text          NOT NULL
                                         CONSTRAINT answers_retrieval_mode_valid
                                         CHECK (retrieval_mode IN ('LEXICAL', 'VECTOR', 'HYBRID')),
    prompt_version         text          NOT NULL,
    generation_provider    text,
    generation_model       text,
    error_code             text,
    warnings               text[]        NOT NULL DEFAULT '{}',
    invalid_citation_count integer       NOT NULL DEFAULT 0
                                         CONSTRAINT answers_invalid_citation_count_valid
                                         CHECK (invalid_citation_count >= 0),
    retrieval_metadata     jsonb,
    latency_ms             bigint,
    input_tokens           integer,
    output_tokens          integer,
    estimated_cost         numeric(12, 6),
    cost_currency          text,
    cost_status            text          NOT NULL DEFAULT 'UNKNOWN'
                                         CONSTRAINT answers_cost_status_valid
                                         CHECK (cost_status IN ('ESTIMATED', 'UNKNOWN')),
    created_at             timestamptz   NOT NULL,
    completed_at           timestamptz,
    CONSTRAINT answers_state_consistency CHECK (
        (status = 'PENDING' AND completed_at IS NULL AND error_code IS NULL
            AND answer_message_id IS NULL AND latency_ms IS NULL AND input_tokens IS NULL
            AND output_tokens IS NULL AND cost_status = 'UNKNOWN')
     OR (status = 'FAILED' AND completed_at IS NOT NULL AND error_code IS NOT NULL
            AND answer_message_id IS NULL)
     OR (status IN ('ANSWERED', 'PARTIAL') AND completed_at IS NOT NULL
            AND error_code IS NULL AND answer_message_id IS NOT NULL)
     OR (status = 'ABSTAINED' AND completed_at IS NOT NULL AND error_code IS NULL)
    ),
    CONSTRAINT answers_cost_consistency CHECK (
        (cost_status = 'ESTIMATED' AND estimated_cost IS NOT NULL AND cost_currency IS NOT NULL)
     OR (cost_status = 'UNKNOWN' AND estimated_cost IS NULL AND cost_currency IS NULL)
    )
);

CREATE INDEX answers_pending_created_at_idx
    ON answers (created_at)
    WHERE status = 'PENDING';

CREATE TABLE answer_evidence (
    id                uuid    PRIMARY KEY,
    answer_id         uuid    NOT NULL
                              CONSTRAINT answer_evidence_answer_id_fkey
                              REFERENCES answers (id) ON DELETE CASCADE,
    snapshot_id       uuid    NOT NULL,
    chunk_id          uuid,
    citation_label    text    NOT NULL,
    relative_path     text    NOT NULL,
    other_paths       text[]  NOT NULL DEFAULT '{}',
    content_hash      text    NOT NULL,
    start_line        integer NOT NULL,
    end_line          integer NOT NULL,
    cited_start_line  integer,
    cited_end_line    integer,
    snippet           text    NOT NULL,
    rank_lexical      integer,
    rank_vector       integer,
    rank_path         integer,
    final_rank        integer NOT NULL,
    validation_status text    NOT NULL
                              CONSTRAINT answer_evidence_validation_status_valid
                              CHECK (validation_status IN ('CITED', 'NOT_CITED', 'INVALID_RANGE')),
    CONSTRAINT answer_evidence_label_key UNIQUE (answer_id, citation_label),
    CONSTRAINT answer_evidence_lines_valid CHECK (start_line >= 1 AND end_line >= start_line),
    -- A narrowed citation exists only for a cited evidence and stays inside its lines.
    CONSTRAINT answer_evidence_cited_lines_valid CHECK (
        (cited_start_line IS NULL AND cited_end_line IS NULL)
     OR (validation_status = 'CITED'
            AND cited_start_line IS NOT NULL AND cited_end_line IS NOT NULL
            AND cited_start_line >= start_line
            AND cited_end_line >= cited_start_line
            AND cited_end_line <= end_line)
    )
);
