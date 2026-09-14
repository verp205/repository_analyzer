package com.ivan_naumenko.repository_analyzer.analysis.controller;

import com.ivan_naumenko.repository_analyzer.analysis.dto.AnalysisTaskResponse;
import com.ivan_naumenko.repository_analyzer.analysis.dto.CreateAnalysisTaskRequest;
import com.ivan_naumenko.repository_analyzer.analysis.dto.AnalysisTaskProgressResponse;
import com.ivan_naumenko.repository_analyzer.analysis.dto.RetryAnalysisTaskRequest;
import com.ivan_naumenko.repository_analyzer.analysis.mapper.TaskMapper;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.orchestrator.AnalysisOrchestrator;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisRecoveryService;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisProgressService;
import com.ivan_naumenko.repository_analyzer.git.model.GitCredentials;
import com.ivan_naumenko.repository_analyzer.security.RepositoryUrlPolicy;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/analysis-tasks")
public class AnalysisTaskController {

    private final AnalysisTaskService analysisTaskService;
    private final AnalysisProgressService progressService;
    private final AnalysisRecoveryService analysisRecoveryService;
    private final TaskMapper taskMapper;
    private final AnalysisOrchestrator orchestrator;
    private final RepositoryUrlPolicy repositoryUrlPolicy;

    @GetMapping
    public List<AnalysisTaskResponse> getAnalysisTasks() {
        return analysisTaskService.getAll().stream()
                .map(taskMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public AnalysisTaskResponse getAnalysisTask(@PathVariable Integer id) {
        return taskMapper.toResponse(analysisTaskService.getById(id));
    }

    @GetMapping("/{id}/progress")
    public AnalysisTaskProgressResponse getProgress(@PathVariable Integer id) {
        return progressService.get(id);
    }

    @PostMapping
    public ResponseEntity<AnalysisTaskResponse> createOrGetTask(@Valid @RequestBody CreateAnalysisTaskRequest request) {

        String repositoryUrl = repositoryUrlPolicy.validate(request.repositoryUrl());
        AnalysisTask task = analysisTaskService.createOrGet(repositoryUrl, request.branch());

        GitCredentials credentials = GitCredentials.from(
                request.gitUsername(),
                request.gitAccessToken()
        );
        boolean started;

        if (task.getStatus() == AnalysisTaskStatus.FAILED) {
            started = credentials == null
                    ? analysisRecoveryService.retryFailedTask(task.getId())
                    : analysisRecoveryService.retryFailedTask(
                            task.getId(),
                            credentials
                    );
        } else if (task.getStatus() == AnalysisTaskStatus.CANCELLED) {
            started = credentials == null
                    ? analysisRecoveryService.retryCancelledTask(task.getId())
                    : analysisRecoveryService.retryCancelledTask(
                            task.getId(),
                            credentials
                    );
        } else {
            started = credentials == null
                    ? orchestrator.start(task.getId())
                    : orchestrator.start(task.getId(), credentials);
        }

        AnalysisTask updatedTask = analysisTaskService.getById(task.getId());
        AnalysisTaskResponse response = taskMapper.toResponse(updatedTask);

        if (started) {
            return ResponseEntity.accepted().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<AnalysisTaskResponse> retryFailedTask(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false)
            RetryAnalysisTaskRequest request
    ) {

        GitCredentials credentials = request == null
                ? null
                : GitCredentials.from(
                        request.gitUsername(),
                        request.gitAccessToken()
                );
        AnalysisTask currentTask = analysisTaskService.getById(id);
        boolean started;

        if (currentTask.getStatus() == AnalysisTaskStatus.CANCELLED) {
            started = credentials == null
                    ? analysisRecoveryService.retryCancelledTask(id)
                    : analysisRecoveryService.retryCancelledTask(
                            id,
                            credentials
                    );
        } else {
            started = credentials == null
                    ? analysisRecoveryService.retryFailedTask(id)
                    : analysisRecoveryService.retryFailedTask(id, credentials);
        }

        AnalysisTaskResponse response = taskMapper.toResponse(
                analysisTaskService.getById(id)
        );

        if (started) {
            return ResponseEntity.accepted().body(response);
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<AnalysisTaskResponse> cancelAnalysis(
            @PathVariable Integer id
    ) {
        boolean cancelled = analysisTaskService.cancel(id);
        AnalysisTaskResponse response = taskMapper.toResponse(
                analysisTaskService.getById(id)
        );

        if (cancelled) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}
