package com.ivan_naumenko.repository_analyzer.analysis.dto;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;

public record AnalysisTaskProgressResponse(
        Integer analysisTaskId,
        AnalysisTaskStatus status,
        long totalCommits,
        long newCommits,
        long processingCommits,
        long completedCommits,
        long failedCommits
) {
}
