package com.ivan_naumenko.repository_analyzer.llm.context.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CompactCommitContext(
        String commitHash,
        String parentHash,
        String message,
        String authorName,
        Instant committedAt,
        int totalAddedLines,
        int totalDeletedLines,
        List<CompactFileContext> files,
        int omittedFileCount
) {

    public CompactCommitContext {
        if (commitHash == null || commitHash.isBlank()) {
            throw new IllegalArgumentException(
                    "commitHash не должен быть пустым"
            );
        }
        if (parentHash != null && parentHash.isBlank()) {
            throw new IllegalArgumentException(
                    "parentHash не может быть пустой строкой"
            );
        }
        if (totalAddedLines < 0 || totalDeletedLines < 0) {
            throw new IllegalArgumentException(
                    "Метрики строк не могут быть отрицательными"
            );
        }
        if (omittedFileCount < 0) {
            throw new IllegalArgumentException(
                    "Количество пропущенных файлов не может быть отрицательным"
            );
        }

        message = Objects.requireNonNullElse(message, "");
        authorName = Objects.requireNonNullElse(authorName, "");
        committedAt = Objects.requireNonNull(
                committedAt,
                "committedAt не должен быть null"
        );
        files = List.copyOf(Objects.requireNonNull(
                files,
                "files не должен быть null"
        ));
    }
}
