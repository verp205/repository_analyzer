package com.ivan_naumenko.repository_analyzer.analysis.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.repository.AnalysisTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AnalysisTaskStatusIntegrationTest {

    private static final Path TEST_DATABASE = createTestDatabasePath();

    @Autowired
    private AnalysisTaskService analysisTaskService;

    @Autowired
    private AnalysisTaskRepository repository;

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
        repository.deleteAll();
    }

    @Test
    void onlyOneConcurrentCallerCanChangeExpectedStatus() throws Exception {
        AnalysisTask task = analysisTaskService.createOrGet(
                "https://example.com/concurrent-project.git",
                "main"
        );
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = callers.submit(() -> {
                start.await();
                return claimTask(task.getId());
            });
            Future<Boolean> second = callers.submit(() -> {
                start.await();
                return claimTask(task.getId());
            });

            start.countDown();

            boolean firstResult = first.get(10, TimeUnit.SECONDS);
            boolean secondResult = second.get(10, TimeUnit.SECONDS);

            assertEquals(1, (firstResult ? 1 : 0) + (secondResult ? 1 : 0));
            assertEquals(
                    AnalysisTaskStatus.CLONING,
                    analysisTaskService.getById(task.getId()).getStatus()
            );
        } finally {
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private boolean claimTask(Integer taskId) {
        return analysisTaskService.updateStatusIfCurrent(
                taskId,
                AnalysisTaskStatus.NEW,
                AnalysisTaskStatus.CLONING
        );
    }

    private static Path createTestDatabasePath() {
        try {
            Path database = Files.createTempFile(
                    "repository-analyzer-status-test-",
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
