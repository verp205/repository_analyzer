package com.ivan_naumenko.repository_analyzer.llm.cache.repository;

import com.ivan_naumenko.repository_analyzer.llm.cache.entity.LlmCacheEntry;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class LlmCacheRepositoryIntegrationTest {

    private static final Path TEST_DATABASE = createTestDatabasePath();

    @Autowired
    private LlmCacheRepository repository;

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
        repository.deleteAll();
    }

    @Test
    void savesAndLoadsCompletedLlmResponseByPromptHash() {
        String promptHash = "a".repeat(64);
        LocalDateTime createdAt = LocalDateTime.of(
                2026,
                7,
                20,
                12,
                0
        );
        LlmCacheEntry entry = entry(promptHash);
        entry.setCreatedAt(createdAt);

        repository.saveAndFlush(entry);
        entityManager.clear();

        LlmCacheEntry stored = repository.findById(promptHash).orElseThrow();
        assertEquals(promptHash, stored.getPromptHash());
        assertEquals("Analyze commit", stored.getPromptText());
        assertEquals("Commit is safe", stored.getResponseText());
        assertEquals(ReportAudience.SECURITY, stored.getAudience());
        assertEquals(createdAt, stored.getCreatedAt());
    }

    private LlmCacheEntry entry(String promptHash) {
        LlmCacheEntry entry = new LlmCacheEntry();
        entry.setPromptHash(promptHash);
        entry.setPromptText("Analyze commit");
        entry.setResponseText("Commit is safe");
        entry.setAudience(ReportAudience.SECURITY);
        entry.setCreatedAt(LocalDateTime.now());
        return entry;
    }

    private static Path createTestDatabasePath() {
        try {
            Path database = Files.createTempFile(
                    "repository-analyzer-llm-cache-test-",
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
