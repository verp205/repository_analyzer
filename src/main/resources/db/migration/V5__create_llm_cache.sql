CREATE TABLE llm_cache (
    prompt_hash    TEXT PRIMARY KEY,
    prompt_text    TEXT NOT NULL,
    response_text  TEXT NOT NULL,
    audience       TEXT NOT NULL,
    created_at     TIMESTAMP NOT NULL,

    CHECK (length(prompt_hash) = 64)
);
