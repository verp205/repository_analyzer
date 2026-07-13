package com.ivan_naumenko.repository_analyzer.analysis.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.repository.AnalysisTaskRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalysisTaskService {

    private final AnalysisTaskRepository repository;

    public AnalysisTask createOrGet(String repositoryUrl, String branch) {

        Optional<AnalysisTask> task = repository.findByRepositoryUrlAndBranch(repositoryUrl, branch);

        if (task.isPresent()) {
            return task.get();
        } else {

            AnalysisTask newTask = new AnalysisTask();

            newTask.setStatus(AnalysisTaskStatus.NEW);
            newTask.setRepositoryUrl(repositoryUrl);
            newTask.setBranch(branch);

            LocalDateTime now = LocalDateTime.now();

            newTask.setCreatedAt(now);
            newTask.setUpdatedAt(now);

            try {
                return repository.saveAndFlush(newTask);
            } catch (DataAccessException e) {
                return repository.findByRepositoryUrlAndBranch(repositoryUrl, branch)
                        .orElseThrow(() -> e);
            }
        }
    }
}
