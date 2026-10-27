ALTER TABLE analysis_task ADD COLUMN revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE analysis_task ADD COLUMN head_commit_hash TEXT;
ALTER TABLE analysis_task ADD COLUMN added_commits INTEGER NOT NULL DEFAULT 0;
ALTER TABLE repository_commit ADD COLUMN reachable BOOLEAN NOT NULL DEFAULT 1;

CREATE TABLE analysis_report_v9 (
    id INTEGER PRIMARY KEY,
    analysis_task_id INTEGER NOT NULL REFERENCES analysis_task(id) ON DELETE CASCADE,
    audience TEXT NOT NULL,
    period_type TEXT NOT NULL DEFAULT 'ALL',
    period_days INTEGER,
    period_from TEXT,
    period_to TEXT,
    period_key TEXT NOT NULL DEFAULT 'ALL',
    analysis_revision INTEGER NOT NULL DEFAULT 0,
    head_commit_hash TEXT,
    commit_ids_json TEXT,
    snapshot_json TEXT,
    facts_json TEXT,
    artifacts_json TEXT,
    status TEXT NOT NULL,
    total_commits INTEGER NOT NULL DEFAULT 0 CHECK (total_commits >= 0),
    processed_commits INTEGER NOT NULL DEFAULT 0 CHECK (processed_commits >= 0 AND processed_commits <= total_commits),
    content TEXT,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (analysis_task_id, audience, period_key, analysis_revision)
);
INSERT INTO analysis_report_v9 (
    id, analysis_task_id, audience, period_type, period_days, period_from, period_to, period_key,
    status, total_commits, processed_commits, content, last_error, created_at, updated_at
)
SELECT id, analysis_task_id, audience, period_type, period_days, period_from, period_to, period_key,
       status, total_commits, processed_commits, content, last_error, created_at, updated_at
FROM analysis_report;
DROP TABLE analysis_report;
ALTER TABLE analysis_report_v9 RENAME TO analysis_report;
CREATE INDEX idx_analysis_report_task ON analysis_report(analysis_task_id);
CREATE INDEX idx_repository_commit_reachable ON repository_commit(analysis_task_id, reachable, processing_status);
