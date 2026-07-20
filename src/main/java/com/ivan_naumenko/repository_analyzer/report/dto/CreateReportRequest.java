package com.ivan_naumenko.repository_analyzer.report.dto;

import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import jakarta.validation.constraints.NotNull;

public record CreateReportRequest(
        @NotNull ReportAudience audience
) {
}
