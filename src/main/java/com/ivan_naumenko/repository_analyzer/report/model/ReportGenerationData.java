package com.ivan_naumenko.repository_analyzer.report.model;

public record ReportGenerationData(
        Integer reportId,
        Integer taskId,
        ReportAudience audience,
        String repositoryUrl,
        String branch
) {
}
