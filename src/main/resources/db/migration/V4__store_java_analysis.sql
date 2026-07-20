CREATE TABLE java_file_analysis (
    changed_file_id       INTEGER PRIMARY KEY,
    package_name          TEXT NOT NULL,
    imports_text          TEXT NOT NULL,
    line_count            INTEGER NOT NULL,
    parsing_successful    BOOLEAN NOT NULL,
    problems_text         TEXT NOT NULL,

    FOREIGN KEY (changed_file_id)
        REFERENCES commit_changed_file(id)
        ON DELETE CASCADE
);

CREATE TABLE java_type_analysis (
    id                     INTEGER PRIMARY KEY,
    java_file_analysis_id  INTEGER NOT NULL,
    type_position          INTEGER NOT NULL,
    name                   TEXT NOT NULL,
    kind                   TEXT NOT NULL,
    modifiers_text         TEXT NOT NULL,
    annotations_text       TEXT NOT NULL,
    constructor_count      INTEGER NOT NULL,

    FOREIGN KEY (java_file_analysis_id)
        REFERENCES java_file_analysis(changed_file_id)
        ON DELETE CASCADE,
    UNIQUE (java_file_analysis_id, type_position)
);

CREATE TABLE java_method_analysis (
    id                     INTEGER PRIMARY KEY,
    java_type_analysis_id  INTEGER NOT NULL,
    method_position        INTEGER NOT NULL,
    name                   TEXT NOT NULL,
    return_type            TEXT NOT NULL,
    parameter_types_text   TEXT NOT NULL,
    modifiers_text         TEXT NOT NULL,
    annotations_text       TEXT NOT NULL,

    FOREIGN KEY (java_type_analysis_id)
        REFERENCES java_type_analysis(id)
        ON DELETE CASCADE,
    UNIQUE (java_type_analysis_id, method_position)
);

CREATE INDEX idx_java_type_analysis_file
    ON java_type_analysis(java_file_analysis_id);

CREATE INDEX idx_java_method_analysis_type
    ON java_method_analysis(java_type_analysis_id);
