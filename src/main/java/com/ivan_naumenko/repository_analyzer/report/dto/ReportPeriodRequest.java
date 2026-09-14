package com.ivan_naumenko.repository_analyzer.report.dto;

import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriod;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriodType;

public record ReportPeriodRequest(
        ReportPeriodType type,
        Integer days,
        String fromTag,
        String toTag
) {

    public ReportPeriod toPeriod() {
        return new ReportPeriod(type, days, fromTag, toTag);
    }
}
