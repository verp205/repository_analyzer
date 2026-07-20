package com.ivan_naumenko.repository_analyzer.insight.cluster.service;

import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileEntity;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiffEntity;
import com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository;
import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.ChangeCluster;
import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.ClusteredCommit;
import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.CommitClustersResponse;
import com.ivan_naumenko.repository_analyzer.insight.cluster.model.ChangeClusterType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommitClusteringService {

    private final AnalysisTaskService taskService;
    private final CommitDiffRepository diffRepository;

    @Transactional(readOnly = true)
    public CommitClustersResponse cluster(Integer taskId) {
        taskService.getById(taskId);
        Map<ChangeClusterType, List<ClusteredCommit>> grouped =
                new EnumMap<>(ChangeClusterType.class);

        for (CommitDiffEntity diff
                : diffRepository.findAllByCommit_AnalysisTask_Id(taskId)) {
            ChangeClusterType type = classify(diff);
            grouped.computeIfAbsent(type, ignored -> new ArrayList<>())
                    .add(new ClusteredCommit(
                            diff.getCommitId(),
                            diff.getCommit().getCommitHash(),
                            diff.getCommit().getMessage(),
                            diff.getChangedFiles().size(),
                            diff.getTotalAddedLines(),
                            diff.getTotalDeletedLines()
                    ));
        }

        List<ChangeCluster> clusters = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ChangeCluster(
                        entry.getKey(),
                        entry.getKey().displayName(),
                        List.copyOf(entry.getValue())
                ))
                .toList();
        int total = clusters.stream()
                .mapToInt(cluster -> cluster.commits().size())
                .sum();

        return new CommitClustersResponse(taskId, total, clusters);
    }

    private ChangeClusterType classify(CommitDiffEntity diff) {
        String signals = (diff.getCommit().getMessage() + " "
                + diff.getChangedFiles().stream()
                .map(this::path)
                .collect(Collectors.joining(" ")))
                .toLowerCase(Locale.ROOT);

        if (containsAny(signals,
                "security", "auth", "oauth", "jwt", "password",
                "permission", "vulnerab")) {
            return ChangeClusterType.SECURITY;
        }
        if (containsAny(signals,
                "/test/", "test.java", "tests/", "junit", "spec")) {
            return ChangeClusterType.TESTS;
        }
        if (containsAny(signals,
                "readme", "docs/", ".md", "documentation")) {
            return ChangeClusterType.DOCUMENTATION;
        }
        if (containsAny(signals,
                "pom.xml", "build.gradle", "settings.gradle", ".yml",
                ".yaml", ".properties", "dockerfile", ".github/")) {
            return ChangeClusterType.BUILD_AND_CONFIGURATION;
        }
        if (containsAny(signals,
                "migration", ".sql", "repository", "entity", "database",
                "/db/")) {
            return ChangeClusterType.DATA;
        }
        if (containsAny(signals,
                "controller", "/api/", "dto", "endpoint", "rest")) {
            return ChangeClusterType.API;
        }
        return ChangeClusterType.CORE;
    }

    private String path(ChangedFileEntity file) {
        return file.getNewPath() != null ? file.getNewPath() : file.getOldPath();
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
