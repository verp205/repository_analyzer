package com.ivan_naumenko.repository_analyzer.diff.service;

import com.ivan_naumenko.repository_analyzer.commit.model.ParentCountRow;
import com.ivan_naumenko.repository_analyzer.commit.repository.CommitParentRepository;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileRow;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitStatsRow;
import com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Загружает статистику изменений задачи без чтения patch-текстов.
 */
@Service
@RequiredArgsConstructor
public class TaskChangeService {

    private final CommitDiffRepository diffRepository;
    private final CommitParentRepository parentRepository;

    @Transactional(readOnly = true)
    public List<CommitChangeSummary> load(Integer taskId) {
        return assemble(taskId, diffRepository.findStatsByTask(taskId), diffRepository.findChangedFilesByTask(taskId));
    }

    @Transactional(readOnly = true)
    public List<CommitChangeSummary> loadForCommits(Integer taskId, List<Integer> ids) {
        if (ids.isEmpty()) return List.of();
        return assemble(taskId, diffRepository.findStatsByIds(ids), diffRepository.findChangedFilesByIds(ids));
    }

    private List<CommitChangeSummary> assemble(Integer taskId, List<CommitStatsRow> rows, List<ChangedFileRow> files) {
        Map<Integer, List<ChangedFileRow>> filesByCommit = new HashMap<>();
        for (ChangedFileRow file : files) {
            filesByCommit.computeIfAbsent(
                    file.commitId(),
                    ignored -> new ArrayList<>()
            ).add(file);
        }

        Map<Integer, Integer> parentCounts = new HashMap<>();
        for (ParentCountRow row : parentRepository.countParentsByTask(taskId)) {
            parentCounts.put(row.commitId(), row.parentCount().intValue());
        }

        List<CommitChangeSummary> result = new ArrayList<>(rows.size());
        for (int position = 0; position < rows.size(); position++) {
            CommitStatsRow row = rows.get(position);
            result.add(new CommitChangeSummary(
                    position,
                    row.commitId(),
                    row.commitHash(),
                    row.message(),
                    row.authorName(),
                    row.authorEmail(),
                    row.committedAt(),
                    valueOrZero(row.addedLines()),
                    valueOrZero(row.deletedLines()),
                    filesByCommit.getOrDefault(row.commitId(), List.of()),
                    parentCounts.getOrDefault(row.commitId(), 0)
            ));
        }
        return result;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
