package com.ivan_naumenko.repository_analyzer.llm.context.model;

import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;

import java.util.Objects;

public record CompactFileContext(
        String path,
        FileChangeType changeType,
        int addedLines,
        int deletedLines,
        String patch,
        boolean patchTruncated,
        CompactJavaFileContext javaAnalysis
) {

    public CompactFileContext {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "Путь изменённого файла не должен быть пустым"
            );
        }
        if (addedLines < 0 || deletedLines < 0) {
            throw new IllegalArgumentException(
                    "Метрики строк файла не могут быть отрицательными"
            );
        }

        changeType = Objects.requireNonNull(
                changeType,
                "changeType не должен быть null"
        );
        patch = Objects.requireNonNullElse(patch, "");
    }
}
