package com.ivan_naumenko.repository_analyzer.analysis.orchestrator;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.commit.service.CommitDiscoveryService;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisOrchestratorTest {

    private static final Integer TASK_ID = 42;
    private static final String REPOSITORY_URL =
            "https://example.com/project.git";
    private static final String BRANCH = "main";

    @Mock
    private AnalysisTaskService analysisTaskService;

    @Mock
    private GitService gitService;

    @Mock
    private CommitDiscoveryService commitDiscoveryService;

    @Mock
    private ExecutorService analysisExecutor;

    private AnalysisOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new AnalysisOrchestrator(
                analysisTaskService,
                gitService,
                commitDiscoveryService,
                analysisExecutor
        );
    }

    @Test
    void runsGitPipelineAndMovesTaskToProcessing() {
        AnalysisTask task = task();
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.NEW,
                AnalysisTaskStatus.CLONING
        )).thenReturn(true);
        when(analysisTaskService.getById(TASK_ID)).thenReturn(task);
        when(gitService.cloneOrUpdateRepository(REPOSITORY_URL, BRANCH))
                .thenReturn(Path.of("repository"));
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.CLONING,
                AnalysisTaskStatus.DISCOVERING_COMMITS
        )).thenReturn(true);
        when(gitService.readHistory(REPOSITORY_URL, BRANCH))
                .thenReturn(List.of());
        when(commitDiscoveryService.saveDiscoveredCommits(TASK_ID, List.of()))
                .thenReturn(0);
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.DISCOVERING_COMMITS,
                AnalysisTaskStatus.PROCESSING
        )).thenReturn(true);
        runSubmittedTaskImmediately();

        boolean started = orchestrator.start(TASK_ID);

        assertTrue(started);
        verify(gitService).cloneOrUpdateRepository(REPOSITORY_URL, BRANCH);
        verify(gitService).readHistory(REPOSITORY_URL, BRANCH);
        verify(commitDiscoveryService).saveDiscoveredCommits(TASK_ID, List.of());
        verify(analysisTaskService).updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.DISCOVERING_COMMITS,
                AnalysisTaskStatus.PROCESSING
        );
    }

    @Test
    void doesNotSubmitTaskWhenAnotherCallerAlreadyClaimedIt() {
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.NEW,
                AnalysisTaskStatus.CLONING
        )).thenReturn(false);

        boolean started = orchestrator.start(TASK_ID);

        assertFalse(started);
        verifyNoInteractions(analysisExecutor, gitService);
        verify(analysisTaskService, never()).getById(any());
    }

    @Test
    void returnsTaskToNewWhenExecutorRejectsIt() {
        RejectedExecutionException rejection =
                new RejectedExecutionException("queue is full");
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.NEW,
                AnalysisTaskStatus.CLONING
        )).thenReturn(true);
        doThrow(rejection).when(analysisExecutor).execute(any(Runnable.class));
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.CLONING,
                AnalysisTaskStatus.NEW
        )).thenReturn(true);

        RejectedExecutionException thrown = assertThrows(
                RejectedExecutionException.class,
                () -> orchestrator.start(TASK_ID)
        );

        assertSame(rejection, thrown);
        verify(analysisTaskService).updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.CLONING,
                AnalysisTaskStatus.NEW
        );
        verifyNoInteractions(gitService);
    }

    @Test
    void marksTaskAsFailedWhenGitOperationFails() {
        AnalysisTask task = task();
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.NEW,
                AnalysisTaskStatus.CLONING
        )).thenReturn(true);
        when(analysisTaskService.getById(TASK_ID)).thenReturn(task);
        when(gitService.cloneOrUpdateRepository(REPOSITORY_URL, BRANCH))
                .thenThrow(new IllegalStateException("git failure"));
        when(analysisTaskService.updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.CLONING,
                AnalysisTaskStatus.FAILED
        )).thenReturn(true);
        runSubmittedTaskImmediately();

        boolean started = orchestrator.start(TASK_ID);

        assertTrue(started);
        verify(analysisTaskService).updateStatusIfCurrent(
                TASK_ID,
                AnalysisTaskStatus.CLONING,
                AnalysisTaskStatus.FAILED
        );
        verify(gitService, never()).readHistory(any(), any());
    }

    private void runSubmittedTaskImmediately() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(analysisExecutor).execute(any(Runnable.class));
    }

    private AnalysisTask task() {
        AnalysisTask task = new AnalysisTask();
        task.setId(TASK_ID);
        task.setRepositoryUrl(REPOSITORY_URL);
        task.setBranch(BRANCH);
        task.setStatus(AnalysisTaskStatus.CLONING);
        return task;
    }
}
