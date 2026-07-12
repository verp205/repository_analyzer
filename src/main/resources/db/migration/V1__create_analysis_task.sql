CREATE TABLE IF NOT EXISTS analysis_task  (
    id INTEGER PRIMARY KEY,
    repository_url TEXT NOT NULL,
    branch TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(repository_url, branch)
);