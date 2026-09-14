package com.ivan_naumenko.repository_analyzer.diff.model;

import java.time.Instant;

public record CommitStatsRow(
        Integer commitId,
        String commitHash,
        String message,
        String authorName,
        String authorEmail,
        Instant committedAt,
        Integer addedLines,
        Integer deletedLines
) {
}
