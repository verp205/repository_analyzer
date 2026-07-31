package com.ivan_naumenko.repository_analyzer.analysis.service;

import com.ivan_naumenko.repository_analyzer.analysis.config.AnalysisProperties;
import com.ivan_naumenko.repository_analyzer.analysis.exception.AnalysisRecoveryException;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskRecoveryCandidate;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.orchestrator.AnalysisOrchestrator;
import com.ivan_naumenko.repository_analyzer.analysis.repository.AnalysisTaskRepository;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.git.model.GitCredentials;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class AnalysisRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(
            AnalysisRecoveryService.class
    );

    private static final List<AnalysisTaskStatus> RECOVERABLE_STATUSES = List.of(
            AnalysisTaskStatus.NEW,
            AnalysisTaskStatus.CLONING,
            AnalysisTaskStatus.DISCOVERING_COMMITS,
            AnalysisTaskStatus.PROCESSING
    );

    private final AnalysisTaskRepository analysisTaskRepository;
    private final AnalysisOrchestrator analysisOrchestrator;
    private final RepositoryCommitRepository repositoryCommitRepository;
    private final AnalysisTaskService analysisTaskService;
    private final AnalysisProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedAnalyses() {

        List<AnalysisTaskRecoveryCandidate> candidates =
                analysisTaskRepository.findRecoveryCandidates(RECOVERABLE_STATUSES);

        for (AnalysisTaskRecoveryCandidate candidate : candidates) {
            try {
                switch (candidate.status()) {
                    case NEW ->  {
                        analysisOrchestrator.start(candidate.taskId());
                    }

                    case CLONING, DISCOVERING_COMMITS -> {
                        boolean reset = analysisTaskService.updateStatusIfCurrent(
                                candidate.taskId(),
                                candidate.status(),
                                AnalysisTaskStatus.NEW
                        );

                        if (reset) {
                            analysisOrchestrator.start(candidate.taskId());
                        }
                    }

                    case PROCESSING -> {
                        repositoryCommitRepository.requeueInterruptedCommits(candidate.taskId(),
                                CommitProcessingStatus.PROCESSING,
                                CommitProcessingStatus.NEW,
                                LocalDateTime.now());

                        boolean reset = analysisTaskService.updateStatusIfCurrent(
                                candidate.taskId(),
                                candidate.status(),
                                AnalysisTaskStatus.NEW
                        );

                        if (reset) {
                            analysisOrchestrator.start(candidate.taskId());
                        }
                    }
                }
            } catch (Exception exception) {
                log.error(
                        "Не удалось восстановить задачу {} в статусе {}",
                        candidate.taskId(),
                        candidate.status(),
                        exception
                );
            }
        }
    }

    public boolean retryFailedTask(Integer taskId) {
        return retryFailedTask(taskId, null);
    }

    public boolean retryFailedTask(
            Integer taskId,
            GitCredentials credentials
    ) {
        boolean credentialsHandedToOrchestrator = false;
        try {
            repositoryCommitRepository.requeueRetryableFailedCommits(taskId,
                    CommitProcessingStatus.FAILED,
                    CommitProcessingStatus.NEW,
                    LocalDateTime.now(),
                    properties.maxAttempts(),
                    AnalysisTaskStatus.FAILED);

            boolean reset = analysisTaskService.updateStatusIfCurrent(
                    taskId,
                    AnalysisTaskStatus.FAILED,
                    AnalysisTaskStatus.NEW
            );

            if (!reset) {
                return false;
            }

            credentialsHandedToOrchestrator = true;
            return credentials == null
                    ? analysisOrchestrator.start(taskId)
                    : analysisOrchestrator.start(taskId, credentials);
        } catch (Exception exception) {
            throw new AnalysisRecoveryException(taskId, exception);
        } finally {
            if (!credentialsHandedToOrchestrator && credentials != null) {
                credentials.close();
            }
        }
    }

    public boolean retryCancelledTask(Integer taskId) {
        return retryCancelledTask(taskId, null);
    }

    public boolean retryCancelledTask(
            Integer taskId,
            GitCredentials credentials
    ) {
        boolean credentialsHandedToOrchestrator = false;

        try {
            if (analysisOrchestrator.isPreparationRunning(taskId)) {
                return false;
            }

            long processingCommits = repositoryCommitRepository
                    .countByAnalysisTask_IdAndProcessingStatusIn(
                            taskId,
                            List.of(CommitProcessingStatus.PROCESSING)
                    );

            if (processingCommits != 0) {
                return false;
            }

            repositoryCommitRepository.requeueRetryableFailedCommits(
                    taskId,
                    CommitProcessingStatus.FAILED,
                    CommitProcessingStatus.NEW,
                    LocalDateTime.now(),
                    properties.maxAttempts(),
                    AnalysisTaskStatus.CANCELLED
            );

            boolean reset = analysisTaskService.updateStatusIfCurrent(
                    taskId,
                    AnalysisTaskStatus.CANCELLED,
                    AnalysisTaskStatus.NEW
            );

            if (!reset) {
                return false;
            }

            credentialsHandedToOrchestrator = true;
            return credentials == null
                    ? analysisOrchestrator.start(taskId)
                    : analysisOrchestrator.start(taskId, credentials);
        } catch (Exception exception) {
            throw new AnalysisRecoveryException(taskId, exception);
        } finally {
            if (!credentialsHandedToOrchestrator && credentials != null) {
                credentials.close();
            }
        }
    }
}
