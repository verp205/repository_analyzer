package com.ivan_naumenko.repository_analyzer.report.model;

public record ReportGenerationData(
        Integer reportId,
        Integer taskId,
        ReportAudience audience,
        String repositoryUrl,
        String branch,
        ReportPeriod period
) {

    public ReportGenerationData {
        period = period == null ? ReportPeriod.all() : period;
    }

    public ReportGenerationData(
            Integer reportId,
            Integer taskId,
            ReportAudience audience,
            String repositoryUrl,
            String branch
    ) {
        this(reportId, taskId, audience, repositoryUrl, branch, ReportPeriod.all());
    }

    /** Используется в JPQL-проекции. */
    public ReportGenerationData(
            Integer reportId,
            Integer taskId,
            ReportAudience audience,
            String repositoryUrl,
            String branch,
            ReportPeriodType periodType,
            Integer periodDays,
            String periodFrom,
            String periodTo
    ) {
        this(
                reportId,
                taskId,
                audience,
                repositoryUrl,
                branch,
                new ReportPeriod(periodType, periodDays, periodFrom, periodTo)
        );
    }
}
