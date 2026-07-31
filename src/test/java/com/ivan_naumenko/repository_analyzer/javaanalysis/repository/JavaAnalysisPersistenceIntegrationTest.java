package com.ivan_naumenko.repository_analyzer.javaanalysis.repository;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.repository.AnalysisTaskRepository;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.commit.repository.CommitParentRepository;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.commit.service.CommitClaimService;
import com.ivan_naumenko.repository_analyzer.commit.service.CommitDiscoveryService;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFile;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileEntity;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiffEntity;
import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository;
import com.ivan_naumenko.repository_analyzer.diff.service.CommitResultService;
import com.ivan_naumenko.repository_analyzer.git.model.GitCommit;
import com.ivan_naumenko.repository_analyzer.javaanalysis.entity.JavaFileAnalysisEntity;
import com.ivan_naumenko.repository_analyzer.javaanalysis.entity.JavaMethodAnalysisEntity;
import com.ivan_naumenko.repository_analyzer.javaanalysis.entity.JavaTypeAnalysisEntity;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.ChangedJavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaFileAnalysis;
import com.ivan_naumenko.repository_analyzer.javaanalysis.model.JavaTypeKind;
import com.ivan_naumenko.repository_analyzer.javaanalysis.service.JavaAnalysisService;
import jakarta.persistence.EntityManager;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class JavaAnalysisPersistenceIntegrationTest {

    private static final String COMMIT_HASH = "9".repeat(40);
    private static final String FILE_PATH = "src/main/java/example/Sample.java";
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
    private JavaAnalysisService javaAnalysisService;

    @Autowired
    private JavaFileAnalysisRepository javaAnalysisRepository;

    @Autowired
    private CommitDiffRepository diffRepository;

    @Autowired
    private CommitParentRepository parentRepository;

    @Autowired
    private RepositoryCommitRepository commitRepository;

    @Autowired
    private AnalysisTaskRepository taskRepository;

    @Autowired
    private EntityManager entityManager;

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
        javaAnalysisRepository.deleteAll();
        diffRepository.deleteAll();
        parentRepository.deleteAll();
        commitRepository.deleteAll();
        taskRepository.deleteAll();
    }

    @Test
    @Transactional
    void savesCompleteJavaAnalysisTreeAndDeletesItWithChangedFile() {
        JavaFileAnalysis analysis = javaAnalysisService.analyze(
                FILE_PATH,
                """
                        package example;

                        import java.util.List;

                        @Deprecated
                        public class Sample {
                            public Sample() {
                            }

                            public List<String> names(int limit) {
                                return List.of();
                            }
                        }
                        """
        );
        Integer commitId = createCompletedCommitDiff(analysis);
        CommitDiffEntity diff = diffRepository.findById(commitId).orElseThrow();
        ChangedFileEntity changedFile = diff.getChangedFiles().getFirst();
        Integer changedFileId = changedFile.getId();
        entityManager.clear();

        JavaFileAnalysisEntity stored = javaAnalysisRepository
                .findById(changedFileId)
                .orElseThrow();
        assertEquals("example", stored.getPackageName());
        assertEquals("java.util.List", stored.getImportsText());
        assertTrue(stored.isParsingSuccessful());
        assertTrue(stored.getProblemsText().isEmpty());
        assertEquals(1, stored.getTypes().size());

        JavaTypeAnalysisEntity type = stored.getTypes().getFirst();
        assertEquals(0, type.getTypePosition());
        assertEquals("Sample", type.getName());
        assertEquals(JavaTypeKind.CLASS, type.getKind());
        assertEquals("public", type.getModifiersText());
        assertEquals("Deprecated", type.getAnnotationsText());
        assertEquals(1, type.getConstructorCount());
        assertEquals(1, type.getMethods().size());

        JavaMethodAnalysisEntity method = type.getMethods().getFirst();
        assertEquals(0, method.getMethodPosition());
        assertEquals("names", method.getName());
        assertEquals("List<String>", method.getReturnType());
        assertEquals("int", method.getParameterTypesText());
        assertEquals("public", method.getModifiersText());

        diffRepository.deleteById(commitId);
        diffRepository.flush();
        entityManager.clear();

        assertFalse(javaAnalysisRepository.existsById(changedFileId));
    }

    private Integer createCompletedCommitDiff(JavaFileAnalysis analysis) {
        var task = analysisTaskService.createOrGet(
                "https://example.com/java-analysis.git",
                "main"
        );
        discoveryService.saveDiscoveredCommits(
                task.getId(),
                List.of(new GitCommit(
                        COMMIT_HASH,
                        List.of(),
                        "Test User",
                        "test@example.com",
                        Instant.parse("2026-07-20T10:00:00Z"),
                        "Add Sample"
                ))
        );
        moveToProcessing(task.getId());

        Integer commitId = claimService.claimRandomCommit(task.getId())
                .orElseThrow();
        ChangedFile changedFile = new ChangedFile(
                FILE_PATH,
                FILE_PATH,
                FileChangeType.MODIFY,
                10,
                1,
                true,
                "@@ -1 +1,10 @@"
        );
        resultService.complete(
                commitId,
                new CommitDiff(
                        COMMIT_HASH,
                        null,
                        List.of(changedFile),
                        10,
                        1,
                        changedFile.patch()
                ),
                List.of(new ChangedJavaFileAnalysis(0, analysis))
        );
        return commitId;
    }

    private void moveToProcessing(Integer taskId) {
        assertTrue(analysisTaskService.updateStatusIfCurrent(
                taskId,
                AnalysisTaskStatus.NEW,
                AnalysisTaskStatus.CLONING
        ));
        assertTrue(analysisTaskService.updateStatusIfCurrent(
                taskId,
                AnalysisTaskStatus.CLONING,
                AnalysisTaskStatus.DISCOVERING_COMMITS
        ));
        assertTrue(analysisTaskService.updateStatusIfCurrent(
                taskId,
                AnalysisTaskStatus.DISCOVERING_COMMITS,
                AnalysisTaskStatus.PROCESSING
        ));
    }

    private static Path createTestDatabasePath() {
        try {
            Path database = Files.createTempFile(
                    "repository-analyzer-java-analysis-test-",
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
