package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Отбирает коммиты, попадающие в период отчёта. */
@Service
@RequiredArgsConstructor
public class ReportPeriodResolver {

    private final GitService gitService;

    public List<CommitChangeSummary> filter(
            ReportGenerationData report,
            List<CommitChangeSummary> commits
    ) {
        ReportPeriod period = report.period();
        return switch (period.type()) {
            case ALL -> commits;
            case LAST_DAYS -> lastDays(commits, period.days());
            case SINCE_TAG -> byHashes(commits, gitService.commitsBetween(
                    report.repositoryUrl(),
                    report.branch(),
                    period.fromTag(),
                    null
            ));
            case BETWEEN_TAGS -> byHashes(commits, gitService.commitsBetween(
                    report.repositoryUrl(),
                    report.branch(),
                    period.fromTag(),
                    period.toTag()
            ));
        };
    }

    /** Отсчёт от самого нового коммита, а не от текущей даты: так период не пустеет у давно не обновлявшихся проектов. */
    private List<CommitChangeSummary> lastDays(List<CommitChangeSummary> commits, int days) {
        Instant latest = commits.stream()
                .map(CommitChangeSummary::committedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (latest == null) {
            return List.of();
        }
        Instant from = latest.minus(Duration.ofDays(days));
        return commits.stream()
                .filter(commit -> commit.committedAt() != null && !commit.committedAt().isBefore(from))
                .toList();
    }

    private List<CommitChangeSummary> byHashes(List<CommitChangeSummary> commits, Set<String> hashes) {
        Set<String> normalized = hashes.stream()
                .map(hash -> hash.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return commits.stream()
                .filter(commit -> normalized.contains(commit.commitHash().toLowerCase(Locale.ROOT)))
                .toList();
    }
}
