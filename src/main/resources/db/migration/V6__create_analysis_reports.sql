CREATE TABLE analysis_report (
    id                 INTEGER PRIMARY KEY,
    analysis_task_id   INTEGER NOT NULL,
    audience           TEXT NOT NULL,
    status             TEXT NOT NULL,
    total_commits      INTEGER NOT NULL DEFAULT 0,
    processed_commits  INTEGER NOT NULL DEFAULT 0,
    content            TEXT,
    last_error         TEXT,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP NOT NULL,

    FOREIGN KEY (analysis_task_id)
        REFERENCES analysis_task(id)
        ON DELETE CASCADE,
    UNIQUE (analysis_task_id, audience),
    CHECK (total_commits >= 0),
    CHECK (processed_commits >= 0),
    CHECK (processed_commits <= total_commits)
);

CREATE INDEX idx_analysis_report_task
    ON analysis_report(analysis_task_id);
