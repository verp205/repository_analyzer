package com.ivan_naumenko.repository_analyzer.commit.model;

public record CommitProcessingData(
        Integer commitId,
        Integer taskId,
        String commitHash,
        String repositoryUrl,
        String branch
) {
}
