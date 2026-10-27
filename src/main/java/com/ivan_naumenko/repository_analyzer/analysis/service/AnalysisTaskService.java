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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalysisTaskService {

    private static final List<AnalysisTaskStatus> CANCELLABLE_STATUSES = List.of(
            AnalysisTaskStatus.NEW,
            AnalysisTaskStatus.CLONING,
            AnalysisTaskStatus.DISCOVERING_COMMITS,
            AnalysisTaskStatus.PROCESSING
    );

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

    public void recordDiscovery(Integer taskId, String headHash, int addedCommits) {
        writeCoordinator.execute(() -> {
            AnalysisTask task = getById(taskId);
            if (!Objects.equals(task.getHeadCommitHash(), headHash)) {
                task.setRevision(task.getRevision() + 1);
                task.setHeadCommitHash(headHash);
            }
            task.setAddedCommits(addedCommits);
            task.setUpdatedAt(LocalDateTime.now());
            repository.saveAndFlush(task);
        });
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

    public boolean cancel(Integer taskId) {
        Objects.requireNonNull(taskId, "taskId не должен быть null");

        return writeCoordinator.execute(
                () -> repository.updateStatusIfCurrentIn(
                        taskId,
                        CANCELLABLE_STATUSES,
                        AnalysisTaskStatus.CANCELLED,
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
