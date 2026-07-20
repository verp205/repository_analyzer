package com.ivan_naumenko.repository_analyzer.report.dto;

import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;

import java.time.LocalDateTime;

public record AnalysisReportResponse(
        Integer id,
        Integer analysisTaskId,
        ReportAudience audience,
        ReportStatus status,
        int totalCommits,
        int processedCommits,
        String content,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
