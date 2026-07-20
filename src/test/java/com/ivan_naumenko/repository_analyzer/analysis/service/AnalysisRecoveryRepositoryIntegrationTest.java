package com.ivan_naumenko.repository_analyzer.analysis.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.repository.AnalysisTaskRepository;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import com.ivan_naumenko.repository_analyzer.commit.repository.CommitParentRepository;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class AnalysisRecoveryRepositoryIntegrationTest {

    private static final Path TEST_DATABASE = createTestDatabasePath();

    @Autowired
    private AnalysisTaskService analysisTaskService;

    @Autowired
    private AnalysisTaskRepository taskRepository;

    @Autowired
    private RepositoryCommitRepository commitRepository;

    @Autowired
    private CommitParentRepository parentRepository;

    @Autowired
    private CommitDiffRepository diffRepository;

    @DynamicPropertySource
    static void useTemporaryDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:sqlite:file:"
                        + TEST_DATABASE
                        + "?foreign_keys=on&journal_mode=WAL&busy_timeout=10000"
        );
    }

    @BeforeEach
    void clearDatabase() {
        diffRepository.deleteAll();
        parentRepository.deleteAll();
        commitRepository.deleteAll();
        taskRepository.deleteAll();
    }

    @Test
    void requeuesOnlyInterruptedProcessingCommitsForSelectedTask() {
        AnalysisTask task = createTask(
                "https://example.com/interrupted.git",
                AnalysisTaskStatus.PROCESSING
        );
        AnalysisTask otherTask = createTask(
                "https://example.com/other.git",
                AnalysisTaskStatus.PROCESSING
        );
        RepositoryCommit interrupted = saveCommit(
                task,
                "a".repeat(40),
                CommitProcessingStatus.PROCESSING,
                1
        );
        RepositoryCommit completed = saveCommit(
                task,
                "b".repeat(40),
                CommitProcessingStatus.COMPLETED,
                1
        );
        RepositoryCommit otherInterrupted = saveCommit(
                otherTask,
                "c".repeat(40),
                CommitProcessingStatus.PROCESSING,
                1
        );

        int updated = commitRepository.requeueInterruptedCommits(
                task.getId(),
                CommitProcessingStatus.PROCESSING,
                CommitProcessingStatus.NEW,
                LocalDateTime.now()
        );

        assertEquals(1, updated);
        assertStatus(interrupted.getId(), CommitProcessingStatus.NEW);
        assertStatus(completed.getId(), CommitProcessingStatus.COMPLETED);
        assertStatus(
                otherInterrupted.getId(),
                CommitProcessingStatus.PROCESSING
        );
    }

    @Test
    void retriesOnlyFailedCommitsBelowConfiguredAttemptLimit() {
        AnalysisTask failedTask = createTask(
                "https://example.com/failed.git",
                AnalysisTaskStatus.FAILED
        );
        RepositoryCommit retryable = saveCommit(
                failedTask,
                "d".repeat(40),
                CommitProcessingStatus.FAILED,
                2
        );
        RepositoryCommit exhausted = saveCommit(
                failedTask,
                "e".repeat(40),
                CommitProcessingStatus.FAILED,
                3
        );

        int updated = commitRepository.requeueRetryableFailedCommits(
                failedTask.getId(),
                CommitProcessingStatus.FAILED,
                CommitProcessingStatus.NEW,
                LocalDateTime.now(),
                3,
                AnalysisTaskStatus.FAILED
        );

        assertEquals(1, updated);
        assertStatus(retryable.getId(), CommitProcessingStatus.NEW);
        assertStatus(exhausted.getId(), CommitProcessingStatus.FAILED);
    }

    @Test
    void doesNotRetryCommitsWhenTaskIsNotFailed() {
        AnalysisTask processingTask = createTask(
                "https://example.com/active.git",
                AnalysisTaskStatus.PROCESSING
        );
        RepositoryCommit failedCommit = saveCommit(
                processingTask,
                "f".repeat(40),
                CommitProcessingStatus.FAILED,
                1
        );

        int updated = commitRepository.requeueRetryableFailedCommits(
                processingTask.getId(),
                CommitProcessingStatus.FAILED,
                CommitProcessingStatus.NEW,
                LocalDateTime.now(),
                3,
                AnalysisTaskStatus.FAILED
        );

        assertEquals(0, updated);
        assertStatus(failedCommit.getId(), CommitProcessingStatus.FAILED);
    }

    private AnalysisTask createTask(
            String repositoryUrl,
            AnalysisTaskStatus status
    ) {
        AnalysisTask task = analysisTaskService.createOrGet(repositoryUrl, "main");
        analysisTaskService.updateStatusIfCurrent(
                task.getId(),
                AnalysisTaskStatus.NEW,
                status
        );
        return analysisTaskService.getById(task.getId());
    }

    private RepositoryCommit saveCommit(
            AnalysisTask task,
            String hash,
            CommitProcessingStatus status,
            int attemptCount
    ) {
        LocalDateTime now = LocalDateTime.now();
        RepositoryCommit commit = new RepositoryCommit();
        commit.setAnalysisTask(task);
        commit.setCommitHash(hash);
        commit.setCommittedAt(Instant.parse("2026-07-20T08:00:00Z"));
        commit.setProcessingStatus(status);
        commit.setAttemptCount(attemptCount);
        commit.setCreatedAt(now);
        commit.setUpdatedAt(now);
        return commitRepository.saveAndFlush(commit);
    }

    private void assertStatus(
            Integer commitId,
            CommitProcessingStatus expectedStatus
    ) {
        RepositoryCommit actual = commitRepository.findById(commitId).orElseThrow();
        assertEquals(expectedStatus, actual.getProcessingStatus());
    }

    private static Path createTestDatabasePath() {
        try {
            Path database = Files.createTempFile(
                    "repository-analyzer-recovery-test-",
                    ".db"
            );
            Files.delete(database);
            database.toFile().deleteOnExit();
            return database;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
