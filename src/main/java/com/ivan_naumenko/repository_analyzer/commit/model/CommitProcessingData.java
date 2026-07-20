package com.ivan_naumenko.repository_analyzer.commit.model;

import java.time.Instant;

public record CommitProcessingData(
        Integer commitId,
        Integer taskId,
        String commitHash,
        String repositoryUrl,
        String branch,
        String authorName,
        Instant committedAt,
        String message
) {
}
