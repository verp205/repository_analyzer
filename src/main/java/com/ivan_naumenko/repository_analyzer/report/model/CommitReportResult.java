package com.ivan_naumenko.repository_analyzer.report.model;

public record CommitReportResult(
        int position,
        Integer commitId,
        String commitHash,
        String message,
        String content
) {
}
