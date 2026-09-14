package com.ivan_naumenko.repository_analyzer.report.dto;

import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriod;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriodType;

public record ReportPeriodResponse(
        ReportPeriodType type,
        Integer days,
        String fromTag,
        String toTag,
        String label
) {

    public static ReportPeriodResponse from(ReportPeriod period) {
        return new ReportPeriodResponse(
                period.type(),
                period.days(),
                period.fromTag(),
                period.toTag(),
                period.label()
        );
    }
}
