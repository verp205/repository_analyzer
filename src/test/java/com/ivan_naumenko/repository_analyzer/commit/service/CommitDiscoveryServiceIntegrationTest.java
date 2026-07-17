package com.ivan_naumenko.repository_analyzer.commit.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.repository.AnalysisTaskRepository;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitParent;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import com.ivan_naumenko.repository_analyzer.commit.repository.CommitParentRepository;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.git.model.GitCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class CommitDiscoveryServiceIntegrationTest {

    private static final Path TEST_DATABASE = createTestDatabasePath();

    @Autowired
    private CommitDiscoveryService discoveryService;

    @Autowired
    private AnalysisTaskService analysisTaskService;

    @Autowired
    private AnalysisTaskRepository taskRepository;

    @Autowired
    private RepositoryCommitRepository commitRepository;

    @Autowired
    private CommitParentRepository parentRepository;

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
        parentRepository.deleteAll();
        commitRepository.deleteAll();
        taskRepository.deleteAll();
    }

    @Test
    @Transactional
    void savesCommitsAndParentsIdempotently() {
        AnalysisTask task = analysisTaskService.createOrGet(
                "https://example.com/history.git",
                "main"
        );
        String rootHash = "a".repeat(40);
        String childHash = "b".repeat(40);
        Instant rootTime = Instant.parse("2026-07-17T09:00:00Z");
        Instant childTime = Instant.parse("2026-07-17T10:00:00Z");
        List<GitCommit> history = List.of(
                new GitCommit(
                        childHash,
                        List.of(rootHash),
                        "Test User",
                        "test@example.com",
                        childTime,
                        "Second commit"
                ),
                new GitCommit(
                        rootHash,
                        List.of(),
                        null,
                        null,
                        rootTime,
                        null
                )
        );

        int firstInsertCount = discoveryService.saveDiscoveredCommits(
                task.getId(),
                history
        );
        int secondInsertCount = discoveryService.saveDiscoveredCommits(
                task.getId(),
                history
        );

        assertEquals(2, firstInsertCount);
        assertEquals(0, secondInsertCount);
        assertEquals(2, commitRepository.count());
        assertEquals(1, parentRepository.count());

        RepositoryCommit child = commitRepository
                .findAllByAnalysisTask_Id(task.getId())
                .stream()
                .filter(commit -> commit.getCommitHash().equals(childHash))
                .findFirst()
                .orElseThrow();
        assertEquals(task.getId(), child.getAnalysisTask().getId());
        assertEquals(CommitProcessingStatus.NEW, child.getProcessingStatus());
        assertEquals(0, child.getAttemptCount());
        assertEquals(childTime, child.getCommittedAt());
        assertNull(child.getLastError());

        CommitParent parent = parentRepository.findAll().getFirst();
        assertEquals(child.getId(), parent.getChildCommit().getId());
        assertEquals(rootHash, parent.getParentHash());
        assertEquals(0, parent.getParentPosition());
    }

    private static Path createTestDatabasePath() {
        try {
            Path database = Files.createTempFile(
                    "repository-analyzer-commit-test-",
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
