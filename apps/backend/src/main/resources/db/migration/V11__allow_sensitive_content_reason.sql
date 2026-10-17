-- Files excluded for a possible secret in their content (D-083).
ALTER TABLE snapshot_files DROP CONSTRAINT snapshot_files_state_consistency;

ALTER TABLE snapshot_files ADD CONSTRAINT snapshot_files_state_consistency CHECK (
    (status = 'INDEXED' AND content_hash IS NOT NULL AND reason IS NULL)
 OR (status = 'UNSUPPORTED'
        AND reason IS NOT NULL
        AND reason IN ('BINARY', 'INVALID_ENCODING', 'LINE_TOO_LONG', 'SENSITIVE_CONTENT')
        AND content_hash IS NULL)
 OR (status = 'FAILED'
        AND reason IS NOT NULL
        AND reason IN ('READ_ERROR', 'CHANGED_DURING_READ')
        AND content_hash IS NULL)
);
