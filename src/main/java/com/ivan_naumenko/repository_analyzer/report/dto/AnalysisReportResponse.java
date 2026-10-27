package com.ivan_naumenko.repository_analyzer.report.dto;

import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;

import java.time.LocalDateTime;

public record AnalysisReportResponse(
        Integer id,
        Integer analysisTaskId,
        String repositoryUrl,
        String branch,
        ReportPeriodResponse period,
        ReportAudience audience,
        ReportStatus status,
        int totalCommits,
        int processedCommits,
        String content,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int analysisRevision,
        String headCommitHash,
        com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts artifacts
) {
    public AnalysisReportResponse(Integer id, Integer analysisTaskId, String repositoryUrl, String branch,
                                  ReportPeriodResponse period, ReportAudience audience, ReportStatus status,
                                  int totalCommits, int processedCommits, String content, String lastError,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, analysisTaskId, repositoryUrl, branch, period, audience, status, totalCommits,
                processedCommits, content, lastError, createdAt, updatedAt, 0, null, null);
    }
}
