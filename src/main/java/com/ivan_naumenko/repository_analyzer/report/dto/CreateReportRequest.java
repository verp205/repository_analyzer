package com.ivan_naumenko.repository_analyzer.report.dto;

import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriod;
import jakarta.validation.constraints.NotNull;

public record CreateReportRequest(
        @NotNull ReportAudience audience,
        ReportPeriodRequest period
) {

    public ReportPeriod toPeriod() {
        return period == null ? ReportPeriod.all() : period.toPeriod();
    }
}
