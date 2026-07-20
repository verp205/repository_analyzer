package com.ivan_naumenko.repository_analyzer.analysis.controller;

import com.ivan_naumenko.repository_analyzer.analysis.dto.AnalysisTaskResponse;
import com.ivan_naumenko.repository_analyzer.analysis.dto.CreateAnalysisTaskRequest;
import com.ivan_naumenko.repository_analyzer.analysis.mapper.TaskMapper;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.orchestrator.AnalysisOrchestrator;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisRecoveryService;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
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

@AllArgsConstructor
@RestController
@RequestMapping("/api/analysis-tasks")
public class AnalysisTaskController {

    private final AnalysisTaskService analysisTaskService;
    private final AnalysisRecoveryService analysisRecoveryService;
    private final TaskMapper taskMapper;
    private final AnalysisOrchestrator orchestrator;

    @GetMapping("/{id}")
    public AnalysisTaskResponse getAnalysisTask(@PathVariable Integer id) {
        return taskMapper.toResponse(analysisTaskService.getById(id));
    }

    @PostMapping
    public ResponseEntity<AnalysisTaskResponse> createOrGetTask(@Valid @RequestBody CreateAnalysisTaskRequest request) {

        AnalysisTask task = analysisTaskService.createOrGet(request.repositoryUrl(), request.branch());

        boolean started = orchestrator.start(task.getId());

        AnalysisTask updatedTask = analysisTaskService.getById(task.getId());
        AnalysisTaskResponse response = taskMapper.toResponse(updatedTask);

        if (started) {
            return ResponseEntity.accepted().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<AnalysisTaskResponse> retryFailedTask(@PathVariable Integer id) {

        boolean started = analysisRecoveryService.retryFailedTask(id);

        AnalysisTaskResponse response = taskMapper.toResponse(
                analysisTaskService.getById(id)
        );

        if (started) {
            return ResponseEntity.accepted().body(response);
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}
