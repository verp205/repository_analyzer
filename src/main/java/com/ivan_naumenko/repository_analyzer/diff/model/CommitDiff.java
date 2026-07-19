package com.ivan_naumenko.repository_analyzer.diff.model;

import java.util.List;
import java.util.Objects;

public record CommitDiff(
        String commitHash,
        String parentHash,
        List<ChangedFile> changedFiles,
        int totalAddedLines,
        int totalDeletedLines,
        String rawDiff
) {

    public CommitDiff {
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
                    "Количество изменённых строк не может быть отрицательным"
            );
        }

        changedFiles = List.copyOf(Objects.requireNonNull(
                changedFiles,
                "changedFiles не должен быть null"
        ));

        int calculatedAddedLines = changedFiles.stream()
                .mapToInt(ChangedFile::addedLines)
                .sum();
        int calculatedDeletedLines = changedFiles.stream()
                .mapToInt(ChangedFile::deletedLines)
                .sum();

        if (totalAddedLines != calculatedAddedLines
                || totalDeletedLines != calculatedDeletedLines) {
            throw new IllegalArgumentException(
                    "Общие метрики должны совпадать с метриками файлов"
            );
        }

        rawDiff = Objects.requireNonNullElse(rawDiff, "");
    }

    public boolean rootCommit() {
        return parentHash == null;
    }
}
