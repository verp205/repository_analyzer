package com.ivan_naumenko.repository_analyzer.diff.model;

import java.time.Instant;
import java.util.List;

/**
 * Лёгкое представление коммита без patch-текста: метаданные, статистика
 * строк и список изменённых файлов.
 */
public record CommitChangeSummary(
        int position,
        Integer commitId,
        String commitHash,
        String message,
        String authorName,
        String authorEmail,
        Instant committedAt,
        int addedLines,
        int deletedLines,
        List<ChangedFileRow> files,
        int parentCount
) {

    public CommitChangeSummary {
        files = files == null ? List.of() : List.copyOf(files);
    }

    public CommitChangeSummary(
            int position,
            Integer commitId,
            String commitHash,
            String message,
            String authorName,
            Instant committedAt,
            int addedLines,
            int deletedLines,
            List<ChangedFileRow> files,
            int parentCount
    ) {
        this(
                position,
                commitId,
                commitHash,
                message,
                authorName,
                null,
                committedAt,
                addedLines,
                deletedLines,
                files,
                parentCount
        );
    }

    public int totalLines() {
        return addedLines + deletedLines;
    }

    public boolean merge() {
        return parentCount > 1;
    }
}
