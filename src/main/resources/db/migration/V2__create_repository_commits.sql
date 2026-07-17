CREATE TABLE repository_commit (
    id                    INTEGER PRIMARY KEY,
    analysis_task_id      INTEGER NOT NULL,
    commit_hash           TEXT NOT NULL,
    author_name           TEXT,
    author_email          TEXT,
    committed_at          TIMESTAMP NOT NULL,
    message               TEXT,
    processing_status     TEXT NOT NULL,
    attempt_count         INTEGER NOT NULL DEFAULT 0,
    last_error            TEXT,
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP NOT NULL,

    FOREIGN KEY (analysis_task_id) REFERENCES analysis_task(id) ON DELETE CASCADE,
    UNIQUE(analysis_task_id, commit_hash)
);

CREATE TABLE commit_parent (
    id                 INTEGER PRIMARY KEY,
    child_commit_id    INTEGER NOT NULL,
    parent_hash        TEXT NOT NULL,
    parent_position    INTEGER NOT NULL,

    FOREIGN KEY (child_commit_id) REFERENCES repository_commit(id) ON DELETE CASCADE,
    UNIQUE(child_commit_id, parent_position)
);

CREATE INDEX IF NOT EXISTS idx_repository_commit_task_status
    ON repository_commit(analysis_task_id, processing_status);

CREATE INDEX IF NOT EXISTS idx_commit_parent_parent_hash
    ON commit_parent(parent_hash);
