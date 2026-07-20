package com.ivan_naumenko.repository_analyzer.insight.cluster.dto;

public record ClusteredCommit(
        Integer id,
        String commitHash,
        String message,
        int changedFiles,
        int addedLines,
        int deletedLines
) {
}
