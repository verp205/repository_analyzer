package com.ivan_naumenko.repository_analyzer.analysis.service;

import com.ivan_naumenko.repository_analyzer.analysis.exception.AnalysisTaskNotFoundException;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.repository.AnalysisTaskRepository;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisTaskService {

    private final AnalysisTaskRepository repository;
    private final SqliteWriteCoordinator writeCoordinator;

    public AnalysisTask createOrGet(String repositoryUrl, String branch) {

        String normalizedRepositoryUrl = normalize(repositoryUrl, "URL репозитория");
        String normalizedBranch = normalize(branch, "Название ветки");

        return writeCoordinator.execute(() -> createOrGetUnderWriteLock(
                normalizedRepositoryUrl,
                normalizedBranch
        ));
    }

    private AnalysisTask createOrGetUnderWriteLock(
            String normalizedRepositoryUrl,
            String normalizedBranch
    ) {
        Optional<AnalysisTask> task = repository.findByRepositoryUrlAndBranch(
                normalizedRepositoryUrl,
                normalizedBranch
        );
        if (task.isPresent()) {
            return task.get();
        } else {

            AnalysisTask newTask = new AnalysisTask();

            newTask.setStatus(AnalysisTaskStatus.NEW);
            newTask.setRepositoryUrl(normalizedRepositoryUrl);
            newTask.setBranch(normalizedBranch);

            LocalDateTime now = LocalDateTime.now();

            newTask.setCreatedAt(now);
            newTask.setUpdatedAt(now);

            try {
                return repository.saveAndFlush(newTask);
            } catch (DataIntegrityViolationException exception) {
                return repository.findByRepositoryUrlAndBranch(
                                normalizedRepositoryUrl,
                                normalizedBranch
                        )
                        .orElseThrow(() -> exception);
            }
        }
    }

    public AnalysisTask getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new AnalysisTaskNotFoundException("Задача не найдена"));
    }

    public List<AnalysisTask> getAll() {
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    public boolean updateStatusIfCurrent(Integer taskId,
                                         AnalysisTaskStatus expectedStatus,
                                         AnalysisTaskStatus newStatus) {

        return writeCoordinator.execute(
                () -> repository.updateStatusIfCurrent(
                        taskId,
                        expectedStatus,
                        newStatus,
                        LocalDateTime.now()
                ) == 1
        );
    }

    private String normalize(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " не должно быть пустым");
        }

        return value.trim();
    }
}
