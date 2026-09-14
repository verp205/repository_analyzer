package com.ivan_naumenko.repository_analyzer.diff.model;

public record ChangedFileRow(
        Integer commitId,
        String oldPath,
        String newPath,
        FileChangeType changeType,
        Integer addedLines,
        Integer deletedLines
) {

    public String path() {
        return newPath != null ? newPath : oldPath;
    }

    public int changedLines() {
        return valueOrZero(addedLines) + valueOrZero(deletedLines);
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
