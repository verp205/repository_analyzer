package com.ivan_naumenko.repository_analyzer.diff.model;

import java.util.Objects;

public record ChangedFile(
        String oldPath,
        String newPath,
        FileChangeType changeType,
        int addedLines,
        int deletedLines,
        boolean javaFile,
        String patch
) {

    public ChangedFile {
        if (oldPath == null && newPath == null) {
            throw new IllegalArgumentException(
                    "Хотя бы один путь изменённого файла должен быть указан"
            );
        }
        if (addedLines < 0 || deletedLines < 0) {
            throw new IllegalArgumentException(
                    "Количество изменённых строк не может быть отрицательным"
            );
        }

        changeType = Objects.requireNonNull(
                changeType,
                "changeType не должен быть null"
        );
        patch = Objects.requireNonNullElse(patch, "");
    }

    public String path() {
        return newPath != null ? newPath : oldPath;
    }
}
