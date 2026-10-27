package com.ivan_naumenko.repository_analyzer.report.grounding;

import com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.Coverage;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;

/** Preserve measured Git statistics, replace only model risk estimates with the second reading. */
public final class ReviewedFacts {
    private ReviewedFacts() {}
    public static RepositoryFactsResponse apply(RepositoryFactsResponse f, Coverage coverage) {
        if (coverage == null || "SOURCES_ONLY".equals(coverage.mode())) return f;
        var values = coverage.reviewedRisks().values();
        var risks = new RepositoryFactsResponse.RiskDistribution(count(values, "HIGH"), count(values, "MEDIUM"),
                count(values, "LOW"), count(values, "NONE"), count(values, "UNKNOWN"));
        return new RepositoryFactsResponse(f.analysisTaskId(), f.repositoryUrl(), f.repositoryName(), f.branch(),
                f.firstCommitAt(), f.lastCommitAt(), f.periodDays(), f.activeDays(), f.totalCommits(), f.analyzedCommits(),
                f.failedCommits(), f.mergeCommits(), f.addedLines(), f.deletedLines(), f.touchedFiles(), f.authorCount(),
                f.authors(), f.activityGranularity(), f.activity(), f.hotspots(), f.fileTypes(), f.largestCommits(),
                risks, f.categories(), f.insightCommits());
    }
    private static int count(java.util.Collection<String> values, String value) {
        return (int) values.stream().filter(value::equals).count();
    }
}
