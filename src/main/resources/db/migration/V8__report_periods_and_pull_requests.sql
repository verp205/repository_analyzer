-- Отчёт теперь определяется ролью и периодом: для одной роли можно держать
-- отчёт за всю историю, за последний квартал и за конкретный релиз.
CREATE TABLE analysis_report_v8 (
    id                 INTEGER PRIMARY KEY,
    analysis_task_id   INTEGER NOT NULL,
    audience           TEXT NOT NULL,
    period_type        TEXT NOT NULL DEFAULT 'ALL',
    period_days        INTEGER,
    period_from        TEXT,
    period_to          TEXT,
    period_key         TEXT NOT NULL DEFAULT 'ALL',
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
    UNIQUE (analysis_task_id, audience, period_key),
    CHECK (total_commits >= 0),
    CHECK (processed_commits >= 0),
    CHECK (processed_commits <= total_commits)
);

INSERT INTO analysis_report_v8 (
    id,
    analysis_task_id,
    audience,
    period_type,
    period_key,
    status,
    total_commits,
    processed_commits,
    content,
    last_error,
    created_at,
    updated_at
)
SELECT id,
       analysis_task_id,
       audience,
       'ALL',
       'ALL',
       status,
       total_commits,
       processed_commits,
       content,
       last_error,
       created_at,
       updated_at
FROM analysis_report;

DROP TABLE analysis_report;

ALTER TABLE analysis_report_v8 RENAME TO analysis_report;

CREATE INDEX idx_analysis_report_task
    ON analysis_report(analysis_task_id);

-- Кэш связи коммита с pull/merge request, чтобы не упираться в лимиты API.
CREATE TABLE commit_pull_request (
    commit_id     INTEGER PRIMARY KEY,
    status        TEXT NOT NULL,
    context_json  TEXT,
    fetched_at    TIMESTAMP NOT NULL,

    FOREIGN KEY (commit_id)
        REFERENCES repository_commit(id)
        ON DELETE CASCADE
);
