package com.ivan_naumenko.repository_analyzer.report.facts.dto;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.ChangeCategory;

import java.time.Instant;
import java.util.List;

/**
 * Точные показатели репозитория, вычисленные без LLM.
 */
public record RepositoryFactsResponse(
        Integer analysisTaskId,
        String repositoryUrl,
        String repositoryName,
        String branch,
        Instant firstCommitAt,
        Instant lastCommitAt,
        int periodDays,
        int activeDays,
        int totalCommits,
        int analyzedCommits,
        int failedCommits,
        int mergeCommits,
        int addedLines,
        int deletedLines,
        int touchedFiles,
        int authorCount,
        List<AuthorFact> authors,
        String activityGranularity,
        List<ActivityFact> activity,
        List<HotspotFact> hotspots,
        List<FileTypeFact> fileTypes,
        List<LargestCommitFact> largestCommits,
        RiskDistribution risks,
        List<CategoryFact> categories,
        int insightCommits
) {

    public record AuthorFact(
            String name,
            int commits,
            int addedLines,
            int deletedLines,
            double share
    ) {
    }

    public record ActivityFact(String period, int commits) {
    }

    public record HotspotFact(
            String path,
            int commits,
            int addedLines,
            int deletedLines
    ) {
    }

    public record FileTypeFact(String type, int files, int changes) {
    }

    public record LargestCommitFact(
            String commitHash,
            String message,
            int addedLines,
            int deletedLines,
            int changedFiles
    ) {
    }

    public record RiskDistribution(
            int high,
            int medium,
            int low,
            int none,
            int unknown
    ) {
    }

    public record CategoryFact(
            ChangeCategory category,
            String displayName,
            int commits
    ) {
    }
}
