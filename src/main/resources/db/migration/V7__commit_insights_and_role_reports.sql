-- Структурированный анализ коммита не зависит от роли читателя и
-- переиспользуется всеми отчётами задачи.
CREATE TABLE commit_insight (
    commit_id       INTEGER PRIMARY KEY,
    prompt_version  TEXT NOT NULL,
    status          TEXT NOT NULL,
    category        TEXT NOT NULL,
    risk_level      TEXT NOT NULL,
    summary         TEXT NOT NULL,
    details_json    TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL,

    FOREIGN KEY (commit_id)
        REFERENCES repository_commit(id)
        ON DELETE CASCADE
);

-- Ролевые запросы по-прежнему хранят аудиторию, общие запросы — нет.
CREATE TABLE llm_cache_v7 (
    prompt_hash    TEXT PRIMARY KEY,
    prompt_text    TEXT NOT NULL,
    response_text  TEXT NOT NULL,
    audience       TEXT,
    created_at     TIMESTAMP NOT NULL,

    CHECK (length(prompt_hash) = 64)
);

INSERT INTO llm_cache_v7 (
    prompt_hash,
    prompt_text,
    response_text,
    audience,
    created_at
)
SELECT prompt_hash, prompt_text, response_text, audience, created_at
FROM llm_cache;

DROP TABLE llm_cache;

ALTER TABLE llm_cache_v7 RENAME TO llm_cache;

-- Отчёты старого формата пересоздаются при следующем открытии.
UPDATE analysis_report
SET status = 'NEW',
    total_commits = 0,
    processed_commits = 0,
    content = NULL,
    last_error = NULL;
