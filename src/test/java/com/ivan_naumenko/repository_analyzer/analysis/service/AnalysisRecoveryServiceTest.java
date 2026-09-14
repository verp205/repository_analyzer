package com.ivan_naumenko.repository_analyzer.analysis.service;

import com.ivan_naumenko.repository_analyzer.analysis.config.AnalysisProperties;
import com.ivan_naumenko.repository_analyzer.analysis.exception.AnalysisRecoveryException;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskRecoveryCandidate;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.orchestrator.AnalysisOrchestrator;
import com.ivan_naumenko.repository_analyzer.analysis.repository.AnalysisTaskRepository;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisRecoveryServiceTest {

    private static final Integer TASK_ID = 42;
    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private AnalysisTaskRepository analysisTaskRepository;

    @Mock
    private AnalysisOrchestrator analysisOrchestrator;

    @Mock
    private RepositoryCommitRepository repositoryCommitRepository;

    @Mock
    private AnalysisTaskService analysisTaskService;

    private AnalysisRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new AnalysisRecoveryService(
                analysisTaskRepository,
                analysisOrchestrator,
                repositoryCommitRepository,
                analysisTaskService,
                new AnalysisProperties(10, 100, MAX_ATTEMPTS)
        );
    }

    @Test
    void startsNewTaskWithoutResettingIt() {
        when(analysisTaskRepository.findRecoveryCandidates(any()))
                .thenReturn(List.of(candidate(TASK_ID, AnalysisTaskStatus.NEW)));

        recoveryService.recoverInterruptedAnalyses();

        verify(analysisOrchestrator).start(TASK_ID);
        verifyNoInteractions(analysisTaskService, repositoryCommitRepository);
    }

    @Test
    void resetsCloningAndDiscoveringTasksBeforeStartingThem() {
        Integer cloningTaskId = 41;
        Integer discoveringTaskId = 42;
        when(analysisTaskRepository.findRecoveryCandidates(any()))
                .thenReturn(List.of(
                        candidate(cloningTaskId, AnalysisTaskStatus.CLONING),
                        candidate(
                                discoveringTaskId,
                                AnalysisTaskStatus.DISCOVERING_COMMITS
                        )
                ));
        when(analysisTaskService.updateStatusIfCurrent(
                cloningTaskId,
                AnalysisTaskStatus.CLONING,
                AnalysisTaskStatus.NEW
        )).thenReturn(true);
        when(analysisTaskService.updateStatusIfCurrent(
                discoveringTaskId,
                AnalysisTaskStatus.DISCOVERING_COMMITS,
                AnalysisTaskStatus.NEW
        )).thenReturn(true);

        recoveryService.recoverInterruptedAnalyses();

        verify(analysisOrchestrator).start(cloningTaskId);
        verify(analysisOrchestrator).start(discoveringTaskId);
        verifyNoInteractions(repositoryCommitRepository);
    }

    @Test
    void requeuesInterruptedCommitsBeforeRestartingProcessingTask() {
        when(analysisTaskRepository.findRecoveryCandidates(any()))
                .thenReturn(List.of(candidate(
                        TASK_ID,
                        AnalysisTaskStatus.PROCESSING
                )));
        when(repositoryCommitRepository.requeueInterruptedCommits(
                eq(TASK_ID),
                eq(CommitProcessingStatus.PROCESSING),
                eq(CommitProcessingStatus.NEW),
                any(LocalDateTime.class)
        )).thenReturn(2);
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.PROCESSING,
                AnalysisTaskStatus.NEW
        )).thenReturn(true);

        recoveryService.recoverInterruptedAnalyses();

        verify(repositoryCommitRepository).requeueInterruptedCommits(
                eq(TASK_ID),
                eq(CommitProcessingStatus.PROCESSING),
                eq(CommitProcessingStatus.NEW),
                any(LocalDateTime.class)
        );
        verify(analysisOrchestrator).start(TASK_ID);
    }

    @Test
    void doesNotStartTaskWhenStatusResetLostTheRace() {
        when(analysisTaskRepository.findRecoveryCandidates(any()))
                .thenReturn(List.of(candidate(
                        TASK_ID,
                        AnalysisTaskStatus.CLONING
                )));
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.CLONING,
                AnalysisTaskStatus.NEW
        )).thenReturn(false);

        recoveryService.recoverInterruptedAnalyses();

        verify(analysisOrchestrator, never()).start(TASK_ID);
    }

    @Test
    void continuesRecoveryAfterOneCandidateFails() {
        Integer failedCandidateId = 41;
        Integer nextCandidateId = 42;
        when(analysisTaskRepository.findRecoveryCandidates(any()))
                .thenReturn(List.of(
                        candidate(failedCandidateId, AnalysisTaskStatus.NEW),
                        candidate(nextCandidateId, AnalysisTaskStatus.NEW)
                ));
        when(analysisOrchestrator.start(failedCandidateId))
                .thenThrow(new RejectedExecutionException("queue is full"));

        recoveryService.recoverInterruptedAnalyses();

        verify(analysisOrchestrator).start(nextCandidateId);
    }

    @Test
    void retriesFailedTaskUsingConfiguredAttemptLimit() {
        when(repositoryCommitRepository.requeueRetryableFailedCommits(
                eq(TASK_ID),
                eq(CommitProcessingStatus.FAILED),
                eq(CommitProcessingStatus.NEW),
                any(LocalDateTime.class),
                eq(MAX_ATTEMPTS),
                eq(AnalysisTaskStatus.FAILED)
        )).thenReturn(2);
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.FAILED,
                AnalysisTaskStatus.NEW
        )).thenReturn(true);
        when(analysisOrchestrator.start(TASK_ID)).thenReturn(true);

        boolean started = recoveryService.retryFailedTask(TASK_ID);

        assertTrue(started);
        verify(analysisOrchestrator).start(TASK_ID);
    }

    @Test
    void doesNotStartRetryWhenTaskIsNoLongerFailed() {
        when(repositoryCommitRepository.requeueRetryableFailedCommits(
                eq(TASK_ID),
                eq(CommitProcessingStatus.FAILED),
                eq(CommitProcessingStatus.NEW),
                any(LocalDateTime.class),
                eq(MAX_ATTEMPTS),
                eq(AnalysisTaskStatus.FAILED)
        )).thenReturn(0);
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.FAILED,
                AnalysisTaskStatus.NEW
        )).thenReturn(false);

        boolean started = recoveryService.retryFailedTask(TASK_ID);

        assertFalse(started);
        verify(analysisOrchestrator, never()).start(TASK_ID);
    }

    @Test
    void wrapsRetryFailureInAnalysisRecoveryException() {
        IllegalStateException cause = new IllegalStateException("database error");
        when(repositoryCommitRepository.requeueRetryableFailedCommits(
                eq(TASK_ID),
                eq(CommitProcessingStatus.FAILED),
                eq(CommitProcessingStatus.NEW),
                any(LocalDateTime.class),
                eq(MAX_ATTEMPTS),
                eq(AnalysisTaskStatus.FAILED)
        )).thenThrow(cause);

        AnalysisRecoveryException thrown = assertThrows(
                AnalysisRecoveryException.class,
                () -> recoveryService.retryFailedTask(TASK_ID)
        );

        assertSame(cause, thrown.getCause());
    }

    @Test
    void restartsCancelledTaskWhenPreparationAndCommitsHaveStopped() {
        when(repositoryCommitRepository
                .countByAnalysisTask_IdAndProcessingStatusIn(
                        eq(TASK_ID),
                        any()
                )).thenReturn(0L);
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.CANCELLED,
                AnalysisTaskStatus.NEW
        )).thenReturn(true);
        when(analysisOrchestrator.start(TASK_ID)).thenReturn(true);

        assertTrue(recoveryService.retryCancelledTask(TASK_ID));

        verify(repositoryCommitRepository).requeueRetryableFailedCommits(
                eq(TASK_ID),
                eq(CommitProcessingStatus.FAILED),
                eq(CommitProcessingStatus.NEW),
                any(LocalDateTime.class),
                eq(MAX_ATTEMPTS),
                eq(AnalysisTaskStatus.CANCELLED)
        );
        verify(analysisOrchestrator).start(TASK_ID);
    }

    @Test
    void doesNotRestartCancelledTaskWhilePreparationIsRunning() {
        when(analysisOrchestrator.isPreparationRunning(TASK_ID))
                .thenReturn(true);

        assertFalse(recoveryService.retryCancelledTask(TASK_ID));

        verifyNoInteractions(repositoryCommitRepository);
        verify(analysisTaskService, never()).updateStatusIfCurrent(
                any(),
                any(),
                any()
        );
    }

    @Test
    void doesNotRestartCancelledTaskWhileCommitIsFinishing() {
        when(repositoryCommitRepository
                .countByAnalysisTask_IdAndProcessingStatusIn(
                        eq(TASK_ID),
                        any()
                )).thenReturn(1L);

        assertFalse(recoveryService.retryCancelledTask(TASK_ID));

        verify(analysisTaskService, never()).updateStatusIfCurrent(
                any(),
                any(),
                any()
        );
        verify(analysisOrchestrator, never()).start(TASK_ID);
    }

    private AnalysisTaskRecoveryCandidate candidate(
            Integer taskId,
            AnalysisTaskStatus status
    ) {
        return new AnalysisTaskRecoveryCandidate(taskId, status);
    }
}
