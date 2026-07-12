package com.ivan_naumenko.repository_analyzer.api.controller;

import com.ivan_naumenko.repository_analyzer.api.dto.AnalysisTaskResponse;
import com.ivan_naumenko.repository_analyzer.api.dto.CreateAnalysisTaskRequest;
import com.ivan_naumenko.repository_analyzer.mapper.TaskMapper;
import com.ivan_naumenko.repository_analyzer.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.service.AnalysisTaskService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/analysis-tasks")
public class AnalysisTaskController {

    private final AnalysisTaskService analysisTaskService;
    private final TaskMapper taskMapper;

    @PostMapping
    public AnalysisTaskResponse createOrGetTask(@Valid @RequestBody CreateAnalysisTaskRequest request) {
        AnalysisTask task = analysisTaskService.createOrGet(request.repositoryUrl(), request.branch());
        return taskMapper.toResponse(task);
    }
}
