package com.ivan_naumenko.repository_analyzer.commit.model;

public record CommitStatusCount(
        CommitProcessingStatus status,
        long count
) {
}
