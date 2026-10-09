package com.ivan_naumenko.repository_analyzer.report.facts.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.ChangeCategory;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDraft;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightStatus;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightService;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileRow;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.diff.service.TaskChangeService;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse.ActivityFact;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse.AuthorFact;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse.CategoryFact;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse.FileTypeFact;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse.HotspotFact;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse.LargestCommitFact;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse.RiskDistribution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RepositoryFactsService {

    private static final int TOP_AUTHORS = 8;
    private static final int TOP_HOTSPOTS = 8;
    private static final int TOP_FILE_TYPES = 6;
    private static final int TOP_LARGEST_COMMITS = 5;
    private static final int MAX_MESSAGE_LENGTH = 120;
    private static final String UNKNOWN_AUTHOR = "Неизвестный автор";

    private final AnalysisTaskService taskService;
    private final RepositoryCommitRepository commitRepository;
    private final TaskChangeService changeService;
    private final CommitInsightService insightService;

    @Transactional(readOnly = true)
    public RepositoryFactsResponse build(Integer taskId) {
        return build(taskId, changeService.load(taskId), true);
    }

    /** Показатели по подмножеству коммитов, например по периоду отчёта. */
    @Transactional(readOnly = true)
    public RepositoryFactsResponse build(Integer taskId, List<CommitChangeSummary> commits) {
        return build(taskId, commits, false);
    }

    private RepositoryFactsResponse build(
            Integer taskId,
            List<CommitChangeSummary> commits,
            boolean wholeHistory
    ) {
        AnalysisTask task = taskService.getById(taskId);
        Set<Integer> commitIds = commits.stream()
                .map(CommitChangeSummary::commitId)
                .collect(Collectors.toSet());
        Map<Integer, CommitInsightDraft> insights = new HashMap<>();
        insightService.findDrafts(taskId, commits.stream().map(CommitChangeSummary::commitId).toList()).forEach((commitId, draft) -> {
            if (commitIds.contains(commitId)) {
                insights.put(commitId, draft);
            }
        });
        List<CommitChangeSummary> ownChanges = commits.stream()
                .filter(commit -> !commit.merge())
                .toList();

        Instant first = commits.stream()
                .map(CommitChangeSummary::committedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        Instant last = commits.stream()
                .map(CommitChangeSummary::committedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        int periodDays = first == null
                ? 0
                : (int) ChronoUnit.DAYS.between(day(first), day(last)) + 1;
        int activeDays = (int) commits.stream()
                .map(CommitChangeSummary::committedAt)
                .filter(Objects::nonNull)
                .map(this::day)
                .distinct()
                .count();

        List<AuthorFact> authors = authors(commits);
        Activity activity = activity(commits, first, last);

        return new RepositoryFactsResponse(
                taskId,
                task.getRepositoryUrl(),
                repositoryName(task.getRepositoryUrl()),
                task.getBranch(),
                first,
                last,
                periodDays,
                activeDays,
                wholeHistory
                        ? (int) commitRepository.countByAnalysisTask_IdAndProcessingStatusIn(
                        taskId,
                        EnumSet.allOf(CommitProcessingStatus.class)
                )
                        : commits.size(),
                commits.size(),
                wholeHistory
                        ? (int) commitRepository.countByAnalysisTask_IdAndProcessingStatusIn(
                        taskId,
                        List.of(CommitProcessingStatus.FAILED)
                )
                        : 0,
                (int) commits.stream().filter(CommitChangeSummary::merge).count(),
                ownChanges.stream().mapToInt(CommitChangeSummary::addedLines).sum(),
                ownChanges.stream().mapToInt(CommitChangeSummary::deletedLines).sum(),
                (int) ownChanges.stream()
                        .flatMap(commit -> commit.files().stream())
                        .map(ChangedFileRow::path)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count(),
                authors.size(),
                authors.stream().limit(TOP_AUTHORS).toList(),
                activity.granularity(),
                activity.points(),
                hotspots(ownChanges),
                fileTypes(ownChanges),
                largestCommits(ownChanges),
                risks(insights.values()),
                categories(insights.values()),
                (int) insights.values().stream()
                        .filter(draft -> draft.status() != CommitInsightStatus.FAILED)
                        .count()
        );
    }

    /**
     * Один человек часто коммитит под разными именами или адресами. Имена и
     * email, встречавшиеся в одном коммите, склеиваются в одного автора;
     * показывается самое частое имя.
     */
    private List<AuthorFact> authors(List<CommitChangeSummary> commits) {
        Map<String, String> parents = new HashMap<>();
        for (CommitChangeSummary commit : commits) {
            String email = emailKey(commit.authorEmail());
            if (email != null) {
                union(parents, nameKey(commit.authorName()), email);
            } else {
                find(parents, nameKey(commit.authorName()));
            }
        }

        Map<String, int[]> stats = new HashMap<>();
        Map<String, Map<String, Integer>> names = new HashMap<>();
        for (CommitChangeSummary commit : commits) {
            String identity = find(parents, nameKey(commit.authorName()));
            int[] values = stats.computeIfAbsent(identity, ignored -> new int[3]);
            values[0]++;
            if (!commit.merge()) {
                values[1] += commit.addedLines();
                values[2] += commit.deletedLines();
            }
            names.computeIfAbsent(identity, ignored -> new HashMap<>())
                    .merge(displayName(commit.authorName()), 1, Integer::sum);
        }

        int total = commits.size();
        return stats.entrySet().stream()
                .map(entry -> new AuthorFact(
                        names.get(entry.getKey()).entrySet().stream()
                                .max(Map.Entry.<String, Integer>comparingByValue()
                                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                                .map(Map.Entry::getKey)
                                .orElse(UNKNOWN_AUTHOR),
                        entry.getValue()[0],
                        entry.getValue()[1],
                        entry.getValue()[2],
                        total == 0
                                ? 0
                                : Math.round(entry.getValue()[0] * 1_000.0 / total) / 10.0
                ))
                .sorted(Comparator.comparingInt(AuthorFact::commits)
                        .reversed()
                        .thenComparing(AuthorFact::name))
                .toList();
    }

    private String displayName(String name) {
        return name == null || name.isBlank() ? UNKNOWN_AUTHOR : name.strip();
    }

    private String nameKey(String name) {
        return "n:" + displayName(name).toLowerCase(Locale.ROOT);
    }

    private String emailKey(String email) {
        return email == null || email.isBlank()
                ? null
                : "e:" + email.strip().toLowerCase(Locale.ROOT);
    }

    private String find(Map<String, String> parents, String key) {
        String root = key;
        while (true) {
            String parent = parents.putIfAbsent(root, root);
            if (parent == null || parent.equals(root)) {
                break;
            }
            root = parent;
        }
        parents.put(key, root);
        return root;
    }

    private void union(Map<String, String> parents, String left, String right) {
        String leftRoot = find(parents, left);
        String rightRoot = find(parents, right);
        if (!leftRoot.equals(rightRoot)) {
            parents.put(rightRoot, leftRoot);
        }
    }

    private List<HotspotFact> hotspots(List<CommitChangeSummary> commits) {
        Map<String, int[]> stats = new HashMap<>();
        for (CommitChangeSummary commit : commits) {
            Set<String> seenInCommit = new HashSet<>();
            for (ChangedFileRow file : commit.files()) {
                String path = file.path();
                if (path == null || isGeneratedFile(path)) {
                    continue;
                }
                int[] values = stats.computeIfAbsent(path, ignored -> new int[3]);
                if (seenInCommit.add(path)) {
                    values[0]++;
                }
                values[1] += file.addedLines() == null ? 0 : file.addedLines();
                values[2] += file.deletedLines() == null ? 0 : file.deletedLines();
            }
        }

        return stats.entrySet().stream()
                .filter(entry -> entry.getValue()[0] > 1)
                .sorted(Comparator
                        .comparingInt((Map.Entry<String, int[]> entry) -> entry.getValue()[0])
                        .thenComparingInt(entry -> entry.getValue()[1] + entry.getValue()[2])
                        .reversed())
                .limit(TOP_HOTSPOTS)
                .map(entry -> new HotspotFact(
                        entry.getKey(),
                        entry.getValue()[0],
                        entry.getValue()[1],
                        entry.getValue()[2]
                ))
                .toList();
    }

    private List<FileTypeFact> fileTypes(List<CommitChangeSummary> commits) {
        Map<String, Set<String>> files = new HashMap<>();
        Map<String, Integer> changes = new HashMap<>();
        for (CommitChangeSummary commit : commits) {
            for (ChangedFileRow file : commit.files()) {
                if (file.path() == null) {
                    continue;
                }
                String type = fileType(file.path());
                files.computeIfAbsent(type, ignored -> new HashSet<>()).add(file.path());
                changes.merge(type, 1, Integer::sum);
            }
        }

        return files.entrySet().stream()
                .map(entry -> new FileTypeFact(
                        entry.getKey(),
                        entry.getValue().size(),
                        changes.getOrDefault(entry.getKey(), 0)
                ))
                .sorted(Comparator.comparingInt(FileTypeFact::changes).reversed())
                .limit(TOP_FILE_TYPES)
                .toList();
    }

    private List<LargestCommitFact> largestCommits(List<CommitChangeSummary> commits) {
        return commits.stream()
                .sorted(Comparator.comparingInt(CommitChangeSummary::totalLines).reversed())
                .limit(TOP_LARGEST_COMMITS)
                .filter(commit -> commit.totalLines() > 0)
                .map(commit -> new LargestCommitFact(
                        commit.commitHash(),
                        firstLine(commit.message()),
                        commit.addedLines(),
                        commit.deletedLines(),
                        commit.files().size()
                ))
                .toList();
    }

    private RiskDistribution risks(Collection<CommitInsightDraft> insights) {
        int high = 0;
        int medium = 0;
        int low = 0;
        int none = 0;
        int unknown = 0;
        for (CommitInsightDraft insight : insights) {
            switch (insight.riskLevel()) {
                case HIGH -> high++;
                case MEDIUM -> medium++;
                case LOW -> low++;
                case NONE -> none++;
                case UNKNOWN -> unknown++;
            }
        }
        return new RiskDistribution(high, medium, low, none, unknown);
    }

    private List<CategoryFact> categories(Collection<CommitInsightDraft> insights) {
        Map<ChangeCategory, Integer> counts = new EnumMap<>(ChangeCategory.class);
        for (CommitInsightDraft insight : insights) {
            if (insight.status() == CommitInsightStatus.FAILED
                    || insight.category() == ChangeCategory.MERGE) {
                continue;
            }
            counts.merge(insight.category(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<ChangeCategory, Integer>comparingByValue().reversed())
                .map(entry -> new CategoryFact(
                        entry.getKey(),
                        entry.getKey().displayName(),
                        entry.getValue()
                ))
                .toList();
    }

    private Activity activity(
            List<CommitChangeSummary> commits,
            Instant first,
            Instant last
    ) {
        if (first == null) {
            return new Activity("DAY", List.of());
        }
        LocalDate start = day(first);
        LocalDate end = day(last);
        long span = ChronoUnit.DAYS.between(start, end) + 1;
        String granularity = span <= 31 ? "DAY" : span <= 183 ? "WEEK" : "MONTH";

        Map<LocalDate, Integer> counts = new HashMap<>();
        for (CommitChangeSummary commit : commits) {
            if (commit.committedAt() != null) {
                counts.merge(bucket(day(commit.committedAt()), granularity), 1, Integer::sum);
            }
        }

        List<ActivityFact> points = new java.util.ArrayList<>();
        LocalDate lastBucket = bucket(end, granularity);
        for (LocalDate cursor = bucket(start, granularity);
             !cursor.isAfter(lastBucket);
             cursor = next(cursor, granularity)) {
            points.add(new ActivityFact(
                    "MONTH".equals(granularity)
                            ? YearMonth.from(cursor).toString()
                            : cursor.toString(),
                    counts.getOrDefault(cursor, 0)
            ));
        }
        return new Activity(granularity, List.copyOf(points));
    }

    private LocalDate bucket(LocalDate date, String granularity) {
        return switch (granularity) {
            case "WEEK" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "MONTH" -> date.withDayOfMonth(1);
            default -> date;
        };
    }

    private LocalDate next(LocalDate date, String granularity) {
        return switch (granularity) {
            case "WEEK" -> date.plusWeeks(1);
            case "MONTH" -> date.plusMonths(1);
            default -> date.plusDays(1);
        };
    }

    private LocalDate day(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private String fileType(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        return name.isBlank() ? "другое" : name;
    }

    private boolean isGeneratedFile(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return name.endsWith(".lock")
                || name.equals("package-lock.json")
                || name.equals("pnpm-lock.yaml")
                || name.equals("go.sum");
    }

    private String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "Без сообщения";
        }
        String line = message.lines().findFirst().orElse("").strip();
        return line.length() > MAX_MESSAGE_LENGTH
                ? line.substring(0, MAX_MESSAGE_LENGTH) + "…"
                : line;
    }

    private String repositoryName(String url) {
        if (url == null) {
            return "Репозиторий";
        }
        String normalized = url.strip().replaceAll("/+$", "").replaceAll("\\.git$", "");
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        return name.isBlank() ? "Репозиторий" : name;
    }

    private record Activity(String granularity, List<ActivityFact> points) {
    }
}
