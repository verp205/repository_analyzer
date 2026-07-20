package com.ivan_naumenko.repository_analyzer.analysis.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AnalysisCompletionService {

    private static final List<CommitProcessingStatus> ACTIVE_STATUSES = List.of(
            CommitProcessingStatus.NEW,
            CommitProcessingStatus.PROCESSING
    );

    private final RepositoryCommitRepository commitRepository;
    private final AnalysisTaskService analysisTaskService;
    private final SqliteWriteCoordinator writeCoordinator;

    public boolean finishIfNoWorkRemaining(Integer taskId) {
        Objects.requireNonNull(taskId, "taskId не должен быть null");

        long activeCommitCount = commitRepository
                .countByAnalysisTask_IdAndProcessingStatusIn(
                        taskId,
                        ACTIVE_STATUSES
                );
        if (activeCommitCount != 0) {
            return false;
        }

        boolean hasFailedCommits = commitRepository
                .existsByAnalysisTask_IdAndProcessingStatus(
                        taskId,
                        CommitProcessingStatus.FAILED
                );
        AnalysisTaskStatus finalStatus = hasFailedCommits
                ? AnalysisTaskStatus.FAILED
                : AnalysisTaskStatus.COMPLETED;

        return writeCoordinator.execute(
                () -> analysisTaskService.updateStatusIfCurrent(
                        taskId,
                        AnalysisTaskStatus.PROCESSING,
                        finalStatus
                )
        );
    }
}
