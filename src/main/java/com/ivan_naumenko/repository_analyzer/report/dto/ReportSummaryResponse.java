package com.ivan_naumenko.repository_analyzer.report.dto;

import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import java.time.LocalDateTime;

/** History navigation does not need to download every report's content and sources. */
public record ReportSummaryResponse(Integer id, ReportAudience audience, ReportStatus status,
                                    int analysisRevision, String headCommitHash, ReportPeriodResponse period,
                                    LocalDateTime createdAt) {}
