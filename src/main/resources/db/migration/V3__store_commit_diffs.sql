CREATE TABLE commit_diff (
    commit_id              INTEGER PRIMARY KEY,
    parent_hash            TEXT,
    total_added_lines      INTEGER NOT NULL,
    total_deleted_lines    INTEGER NOT NULL,
    raw_diff               TEXT NOT NULL,
    created_at             TIMESTAMP NOT NULL,
    FOREIGN KEY (commit_id) REFERENCES repository_commit(id) ON DELETE CASCADE
);

CREATE TABLE commit_changed_file (
    id                     INTEGER PRIMARY KEY,
    commit_id              INTEGER NOT NULL,
    file_position          INTEGER NOT NULL,
    old_path               TEXT,
    new_path               TEXT,
    change_type            TEXT NOT NULL,
    added_lines            INTEGER NOT NULL,
    deleted_lines          INTEGER NOT NULL,
    java_file              BOOLEAN NOT NULL,
    patch                  TEXT NOT NULL,
    FOREIGN KEY (commit_id) REFERENCES commit_diff(commit_id) ON DELETE CASCADE,
    UNIQUE (commit_id, file_position)
);

CREATE INDEX idx_commit_changed_file_commit
    ON commit_changed_file(commit_id);
