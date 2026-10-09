package com.ivan_naumenko.repository_analyzer.report.repository;

import com.ivan_naumenko.repository_analyzer.report.model.*;
import java.time.LocalDateTime;

public interface ReportSummaryView {
    Integer getId();
    ReportAudience getAudience();
    ReportStatus getStatus();
    int getAnalysisRevision();
    String getHeadCommitHash();
    ReportPeriodType getPeriodType();
    Integer getPeriodDays();
    String getPeriodFrom();
    String getPeriodTo();
    LocalDateTime getCreatedAt();
}
