package com.ivan_naumenko.repository_analyzer.analysis.model;

public record AnalysisTaskRecoveryCandidate(
        Integer taskId,
        AnalysisTaskStatus status
) {
}