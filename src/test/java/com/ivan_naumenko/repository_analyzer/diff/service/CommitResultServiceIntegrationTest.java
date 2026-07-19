package com.ivan_naumenko.repository_analyzer.diff.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.repository.AnalysisTaskRepository;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import com.ivan_naumenko.repository_analyzer.commit.repository.CommitParentRepository;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.commit.service.CommitClaimService;
import com.ivan_naumenko.repository_analyzer.commit.service.CommitDiscoveryService;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiffEntity;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFile;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CommitResultServiceIntegrationTest {

    private static final String COMMIT_HASH = "d".repeat(40);
    private static final Path TEST_DATABASE = createTestDatabasePath();

    @Autowired
    private AnalysisTaskService analysisTaskService;

    @Autowired
    private CommitDiscoveryService discoveryService;

    @Autowired
    private CommitClaimService claimService;

    @Autowired
    private CommitResultService resultService;

    @Autowired
    private CommitDiffRepository diffRepository;

    @Autowired
    private RepositoryCommitRepository commitRepository;

    @Autowired
    private CommitParentRepository parentRepository;

    @Autowired
    private AnalysisTaskRepository taskRepository;

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
    @Transactional
    void savesDiffAndCompletesCommitAtomically() {
        RepositoryCommit commit = createAndClaimCommit();
        ChangedFile changedFile = new ChangedFile(
                "src/Sample.java",
                "src/Sample.java",
                FileChangeType.MODIFY,
                2,
                1,
                true,
                "@@ -1 +1,2 @@"
        );
        CommitDiff diff = new CommitDiff(
                COMMIT_HASH,
                "c".repeat(40),
                List.of(changedFile),
                2,
                1,
                changedFile.patch()
        );

        resultService.complete(commit.getId(), diff);

        RepositoryCommit completed = commitRepository
                .findById(commit.getId())
                .orElseThrow();
        assertEquals(
                CommitProcessingStatus.COMPLETED,
                completed.getProcessingStatus()
        );
        assertNull(completed.getLastError());

        CommitDiffEntity stored = diffRepository
                .findById(commit.getId())
                .orElseThrow();
        assertEquals(diff.parentHash(), stored.getParentHash());
        assertEquals(2, stored.getTotalAddedLines());
        assertEquals(1, stored.getTotalDeletedLines());
        assertEquals(diff.rawDiff(), stored.getRawDiff());
        assertEquals(1, stored.getChangedFiles().size());
        assertEquals(
                "src/Sample.java",
                stored.getChangedFiles().getFirst().getNewPath()
        );
        assertTrue(stored.getChangedFiles().getFirst().isJavaFile());
    }

    @Test
    void storesFailureWithoutCreatingDiff() {
        RepositoryCommit commit = createAndClaimCommit();

        boolean updated = resultService.fail(
                commit.getId(),
                new IllegalStateException("cannot read diff")
        );

        assertTrue(updated);
        RepositoryCommit failed = commitRepository
                .findById(commit.getId())
                .orElseThrow();
        assertEquals(
                CommitProcessingStatus.FAILED,
                failed.getProcessingStatus()
        );
        assertTrue(failed.getLastError().contains("cannot read diff"));
        assertFalse(diffRepository.existsById(commit.getId()));
    }

    private RepositoryCommit createAndClaimCommit() {
        AnalysisTask task = analysisTaskService.createOrGet(
                "https://example.com/result.git",
                "main"
        );
        GitCommit gitCommit = new GitCommit(
                COMMIT_HASH,
                List.of(),
                "Test User",
                "test@example.com",
                Instant.parse("2026-07-19T12:00:00Z"),
                "Test commit"
        );
        discoveryService.saveDiscoveredCommits(task.getId(), List.of(gitCommit));

        Integer claimedId = claimService.claimRandomCommit(task.getId())
                .orElseThrow();
        return commitRepository.findById(claimedId).orElseThrow();
    }

    private static Path createTestDatabasePath() {
        try {
            Path database = Files.createTempFile(
                    "repository-analyzer-result-test-",
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
