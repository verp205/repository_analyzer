package com.ivan_naumenko.repository_analyzer.insight.graph.dto;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;

import java.time.Instant;

public record CommitGraphNode(
        Integer id,
        String commitHash,
        String message,
        String authorName,
        Instant committedAt,
        CommitProcessingStatus processingStatus
) {
}
