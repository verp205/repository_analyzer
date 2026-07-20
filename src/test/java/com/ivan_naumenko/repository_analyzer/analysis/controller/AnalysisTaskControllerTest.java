package com.ivan_naumenko.repository_analyzer.analysis.controller;

import com.ivan_naumenko.repository_analyzer.analysis.exception.AnalysisRecoveryException;
import com.ivan_naumenko.repository_analyzer.analysis.exception.AnalysisTaskNotFoundException;
import com.ivan_naumenko.repository_analyzer.analysis.exception.GlobalExceptionHandler;
import com.ivan_naumenko.repository_analyzer.analysis.mapper.TaskMapper;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.orchestrator.AnalysisOrchestrator;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisRecoveryService;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnalysisTaskControllerTest {

    private static final Integer TASK_ID = 15;
    private static final String REPOSITORY_URL =
            "https://example.com/project.git";
    private static final String BRANCH = "main";

    @Mock
    private AnalysisTaskService analysisTaskService;

    @Mock
    private AnalysisRecoveryService analysisRecoveryService;

    @Mock
    private AnalysisOrchestrator orchestrator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AnalysisTaskController controller = new AnalysisTaskController(
                analysisTaskService,
                analysisRecoveryService,
                new TaskMapper(),
                orchestrator
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsAcceptedWhenNewAnalysisWasStarted() throws Exception {
        AnalysisTask newTask = task(AnalysisTaskStatus.NEW);
        AnalysisTask cloningTask = task(AnalysisTaskStatus.CLONING);
        when(analysisTaskService.createOrGet(REPOSITORY_URL, BRANCH))
                .thenReturn(newTask);
        when(orchestrator.start(TASK_ID)).thenReturn(true);
        when(analysisTaskService.getById(TASK_ID)).thenReturn(cloningTask);

        mockMvc.perform(post("/api/analysis-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "repositoryUrl": "https://example.com/project.git",
                                  "branch": "main"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(TASK_ID))
                .andExpect(jsonPath("$.repositoryUrl").value(REPOSITORY_URL))
                .andExpect(jsonPath("$.branch").value(BRANCH))
                .andExpect(jsonPath("$.status").value("CLONING"));

        verify(orchestrator).start(TASK_ID);
        verify(analysisTaskService).getById(TASK_ID);
    }

    @Test
    void returnsOkWhenAnalysisWasAlreadyStarted() throws Exception {
        AnalysisTask processingTask = task(AnalysisTaskStatus.PROCESSING);
        when(analysisTaskService.createOrGet(REPOSITORY_URL, BRANCH))
                .thenReturn(processingTask);
        when(orchestrator.start(TASK_ID)).thenReturn(false);
        when(analysisTaskService.getById(TASK_ID)).thenReturn(processingTask);

        mockMvc.perform(post("/api/analysis-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "repositoryUrl": "https://example.com/project.git",
                                  "branch": "main"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        verify(orchestrator).start(TASK_ID);
    }

    @Test
    void returnsTaskById() throws Exception {
        when(analysisTaskService.getById(TASK_ID))
                .thenReturn(task(AnalysisTaskStatus.DISCOVERING_COMMITS));

        mockMvc.perform(get("/api/analysis-tasks/{id}", TASK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TASK_ID))
                .andExpect(jsonPath("$.status").value("DISCOVERING_COMMITS"));
    }

    @Test
    void returnsNotFoundWhenTaskDoesNotExist() throws Exception {
        when(analysisTaskService.getById(999))
                .thenThrow(new AnalysisTaskNotFoundException("Задача не найдена"));

        mockMvc.perform(get("/api/analysis-tasks/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Задача не найдена"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void returnsServiceUnavailableWhenExecutorQueueIsFull() throws Exception {
        AnalysisTask newTask = task(AnalysisTaskStatus.NEW);
        when(analysisTaskService.createOrGet(REPOSITORY_URL, BRANCH))
                .thenReturn(newTask);
        when(orchestrator.start(TASK_ID))
                .thenThrow(new RejectedExecutionException("Очередь заполнена"));

        mockMvc.perform(post("/api/analysis-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "repositoryUrl": "https://example.com/project.git",
                                  "branch": "main"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Очередь заполнена"));

        verify(analysisTaskService, never()).getById(TASK_ID);
    }

    @Test
    void returnsAcceptedWhenFailedTaskRetryWasStarted() throws Exception {
        when(analysisRecoveryService.retryFailedTask(TASK_ID)).thenReturn(true);
        when(analysisTaskService.getById(TASK_ID))
                .thenReturn(task(AnalysisTaskStatus.CLONING));

        mockMvc.perform(post("/api/analysis-tasks/{id}/retry", TASK_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(TASK_ID))
                .andExpect(jsonPath("$.status").value("CLONING"));

        verify(analysisRecoveryService).retryFailedTask(TASK_ID);
    }

    @Test
    void returnsConflictWhenTaskCannotBeRetried() throws Exception {
        when(analysisRecoveryService.retryFailedTask(TASK_ID)).thenReturn(false);
        when(analysisTaskService.getById(TASK_ID))
                .thenReturn(task(AnalysisTaskStatus.COMPLETED));

        mockMvc.perform(post("/api/analysis-tasks/{id}/retry", TASK_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.id").value(TASK_ID))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void returnsInternalServerErrorWhenRetryFails() throws Exception {
        when(analysisRecoveryService.retryFailedTask(TASK_ID))
                .thenThrow(new AnalysisRecoveryException(
                        TASK_ID,
                        new IllegalStateException("database error")
                ));

        mockMvc.perform(post("/api/analysis-tasks/{id}/retry", TASK_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value(
                        "Не удалось восстановить задачу анализа: " + TASK_ID
                ));

        verify(analysisTaskService, never()).getById(TASK_ID);
    }

    @Test
    void rejectsRequestWithBlankFields() throws Exception {
        mockMvc.perform(post("/api/analysis-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "repositoryUrl": " ",
                                  "branch": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(orchestrator, never()).start(TASK_ID);
    }

    private AnalysisTask task(AnalysisTaskStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
        AnalysisTask task = new AnalysisTask();
        task.setId(TASK_ID);
        task.setRepositoryUrl(REPOSITORY_URL);
        task.setBranch(BRANCH);
        task.setStatus(status);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }
}
