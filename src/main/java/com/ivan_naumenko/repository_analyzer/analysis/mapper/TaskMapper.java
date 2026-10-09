package com.ivan_naumenko.repository_analyzer.analysis.mapper;

import com.ivan_naumenko.repository_analyzer.analysis.dto.AnalysisTaskResponse;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

    public AnalysisTaskResponse toResponse(AnalysisTask task) {

        AnalysisTaskResponse response = new AnalysisTaskResponse();

        response.setId(task.getId());
        response.setRepositoryUrl(task.getRepositoryUrl());
        response.setBranch(task.getBranch());
        response.setStatus(task.getStatus());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
        response.setRevision(task.getRevision());
        response.setHeadCommitHash(task.getHeadCommitHash());
        response.setAddedCommits(task.getAddedCommits());

        return response;
    }
}
