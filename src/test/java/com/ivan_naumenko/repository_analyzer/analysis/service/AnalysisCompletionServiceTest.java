package com.ivan_naumenko.repository_analyzer.analysis.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisCompletionServiceTest {

    private static final Integer TASK_ID = 7;

    @Mock
    private RepositoryCommitRepository commitRepository;

    @Mock
    private AnalysisTaskService analysisTaskService;

    @Test
    void doesNotFinishWhileActiveCommitsRemain() {
        AnalysisCompletionService service = service();
        when(commitRepository.countByAnalysisTask_IdAndProcessingStatusIn(
                eq(TASK_ID),
                any(Collection.class)
        )).thenReturn(1L);

        assertFalse(service.finishIfNoWorkRemaining(TASK_ID));

        verify(analysisTaskService, never()).updateStatusIfCurrent(
                any(),
                any(),
                any()
        );
    }

    @Test
    void completesTaskWhenEveryCommitSucceeded() {
        AnalysisCompletionService service = service();
        when(commitRepository.countByAnalysisTask_IdAndProcessingStatusIn(
                eq(TASK_ID),
                any(Collection.class)
        )).thenReturn(0L);
        when(commitRepository.existsByAnalysisTask_IdAndProcessingStatus(
                TASK_ID,
                CommitProcessingStatus.FAILED
        )).thenReturn(false);
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.PROCESSING,
                AnalysisTaskStatus.COMPLETED
        )).thenReturn(true);

        assertTrue(service.finishIfNoWorkRemaining(TASK_ID));
    }

    @Test
    void failsTaskWhenAtLeastOneCommitFailed() {
        AnalysisCompletionService service = service();
        when(commitRepository.countByAnalysisTask_IdAndProcessingStatusIn(
                eq(TASK_ID),
                any(Collection.class)
        )).thenReturn(0L);
        when(commitRepository.existsByAnalysisTask_IdAndProcessingStatus(
                TASK_ID,
                CommitProcessingStatus.FAILED
        )).thenReturn(true);
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.PROCESSING,
                AnalysisTaskStatus.FAILED
        )).thenReturn(true);

        assertTrue(service.finishIfNoWorkRemaining(TASK_ID));
    }

    private AnalysisCompletionService service() {
        return new AnalysisCompletionService(
                commitRepository,
                analysisTaskService,
                new SqliteWriteCoordinator()
        );
    }
}
