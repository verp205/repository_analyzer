package com.ivan_naumenko.repository_analyzer.report.orchestrator;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.repository.AnalysisTaskRepository;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisCompletionService;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import com.ivan_naumenko.repository_analyzer.commit.repository.CommitParentRepository;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.commit.service.CommitClaimService;
import com.ivan_naumenko.repository_analyzer.commit.service.CommitDiscoveryService;
import com.ivan_naumenko.repository_analyzer.commitinsight.dto.CommitInsightResponse;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightStatus;
import com.ivan_naumenko.repository_analyzer.commitinsight.repository.CommitInsightRepository;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightService;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFile;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitDiff;
import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository;
import com.ivan_naumenko.repository_analyzer.diff.service.CommitResultService;
import com.ivan_naumenko.repository_analyzer.git.model.GitCommit;
import com.ivan_naumenko.repository_analyzer.javaanalysis.repository.JavaFileAnalysisRepository;
import com.ivan_naumenko.repository_analyzer.llm.cache.repository.LlmCacheRepository;
import com.ivan_naumenko.repository_analyzer.report.dto.AnalysisReportResponse;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.facts.service.RepositoryFactsService;
import com.ivan_naumenko.repository_analyzer.report.model.AnalysisReport;
import com.ivan_naumenko.repository_analyzer.forge.repository.CommitPullRequestRepository;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriod;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriodType;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.repository.AnalysisReportRepository;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ReportGenerationIntegrationTest {

    private static final String COMMIT_HASH = "e".repeat(40);
    private static final Path TEST_DATABASE = createTestDatabasePath();

    @Autowired
    private AnalysisTaskService taskService;

    @Autowired
    private AnalysisCompletionService completionService;

    @Autowired
    private CommitDiscoveryService discoveryService;

    @Autowired
    private CommitClaimService claimService;

    @Autowired
    private CommitResultService resultService;

    @Autowired
    private AnalysisReportService reportService;

    @Autowired
    private ReportOrchestrator orchestrator;

    @Autowired
    private CommitInsightService insightService;

    @Autowired
    private RepositoryFactsService factsService;

    @Autowired
    private AnalysisReportRepository reportRepository;

    @Autowired
    private CommitInsightRepository insightRepository;

    @Autowired
    private LlmCacheRepository cacheRepository;

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

    @DynamicPropertySource
    static void configureTestApplication(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:sqlite:file:"
                        + TEST_DATABASE
                        + "?foreign_keys=on&journal_mode=WAL&busy_timeout=10000"
        );
        registry.add("repository-analyzer.llm.provider", () -> "stub");
    }

    @Autowired
    private CommitPullRequestRepository pullRequestRepository;

    @BeforeEach
    void clearDatabase() {
        reportRepository.deleteAll();
        insightRepository.deleteAll();
        pullRequestRepository.deleteAll();
        cacheRepository.deleteAll();
        javaAnalysisRepository.deleteAll();
        diffRepository.deleteAll();
        parentRepository.deleteAll();
        commitRepository.deleteAll();
        taskRepository.deleteAll();
    }

    @Test
    void buildsRoleReportFromStructuredCommitInsights() throws Exception {
        AnalysisTask task = createCompletedTask();
        AnalysisReport report = reportService.createOrGet(
                task.getId(),
                ReportAudience.DEVELOPER
        );

        assertTrue(orchestrator.start(report.getId()));

        AnalysisReportResponse response = waitForCompletion(
                report.getId(),
                Duration.ofSeconds(5)
        );

        assertEquals(ReportStatus.COMPLETED, response.status());
        assertEquals(1, response.totalCommits());
        assertEquals(1, response.processedCommits());
        assertEquals("https://example.com/report.git", response.repositoryUrl());
        assertEquals("main", response.branch());
        assertNotNull(response.content());
        assertTrue(response.content().startsWith("## Главное"));
        assertFalse(response.content().contains("# Итоговый"));

        List<CommitInsightResponse> insights = insightService.findForTask(task.getId());
        assertEquals(1, insights.size());
        assertEquals(COMMIT_HASH, insights.getFirst().commitHash());
        assertEquals(CommitInsightStatus.ANALYZED, insights.getFirst().status());
        assertTrue(insights.getFirst().summary().contains("Демонстрационный"));

        RepositoryFactsResponse facts = factsService.build(task.getId());
        assertEquals("report", facts.repositoryName());
        assertEquals(1, facts.totalCommits());
        assertEquals(1, facts.insightCommits());
        assertEquals(2, facts.addedLines());
        assertEquals(1, facts.risks().low());

        assertEquals(2, cacheRepository.count());
    }

    @Test
    void reusesCommitInsightsAcrossAudiences() throws Exception {
        AnalysisTask task = createCompletedTask();
        AnalysisReport developer = reportService.createOrGet(
                task.getId(),
                ReportAudience.DEVELOPER
        );
        AnalysisReport security = reportService.createOrGet(
                task.getId(),
                ReportAudience.SECURITY
        );

        assertNotEquals(developer.getId(), security.getId());
        assertTrue(orchestrator.start(developer.getId()));
        assertTrue(orchestrator.start(security.getId()));

        AnalysisReportResponse developerResponse = waitForCompletion(
                developer.getId(),
                Duration.ofSeconds(5)
        );
        AnalysisReportResponse securityResponse = waitForCompletion(
                security.getId(),
                Duration.ofSeconds(5)
        );

        assertEquals(ReportStatus.COMPLETED, developerResponse.status());
        assertEquals(ReportStatus.COMPLETED, securityResponse.status());
        assertEquals(ReportAudience.DEVELOPER, developerResponse.audience());
        assertEquals(ReportAudience.SECURITY, securityResponse.audience());
        assertEquals(1, insightRepository.count());
        // Один общий анализ коммита и по одному ролевому синтезу.
        assertEquals(3, cacheRepository.count());
    }

    @Test
    void keepsSeparateReportsPerPeriod() throws Exception {
        AnalysisTask task = createCompletedTask();
        ReportPeriod lastMonth = new ReportPeriod(ReportPeriodType.LAST_DAYS, 30, null, null);
        AnalysisReport wholeHistory = reportService.createOrGet(task.getId(), ReportAudience.ANALYST);
        AnalysisReport monthly = reportService.createOrGet(task.getId(), ReportAudience.ANALYST, lastMonth);

        assertNotEquals(wholeHistory.getId(), monthly.getId());
        assertEquals(
                monthly.getId(),
                reportService.createOrGet(task.getId(), ReportAudience.ANALYST, lastMonth).getId()
        );

        assertTrue(orchestrator.start(monthly.getId()));
        AnalysisReportResponse response = waitForCompletion(monthly.getId(), Duration.ofSeconds(5));

        assertEquals(ReportStatus.COMPLETED, response.status());
        assertEquals(ReportPeriodType.LAST_DAYS, response.period().type());
        assertEquals("последний месяц истории", response.period().label());
        assertEquals(1, response.totalCommits());
    }

    private AnalysisTask createCompletedTask() {
        AnalysisTask task = taskService.createOrGet(
                "https://example.com/report.git",
                "main"
        );
        moveToProcessing(task.getId());

        discoveryService.saveDiscoveredCommits(
                task.getId(),
                List.of(new GitCommit(
                        COMMIT_HASH,
                        List.of(),
                        "Test User",
                        "test@example.com",
                        Instant.parse("2026-07-20T10:00:00Z"),
                        "Document the analyzer"
                ))
        );

        Integer commitId = claimService.claimRandomCommit(task.getId())
                .orElseThrow();
        RepositoryCommit commit = commitRepository.findById(commitId)
                .orElseThrow();
        ChangedFile file = new ChangedFile(
                "README.md",
                "README.md",
                FileChangeType.MODIFY,
                2,
                1,
                false,
                "@@ -1 +1,2 @@\n-old\n+new"
        );
        resultService.complete(
                commit.getId(),
                new CommitDiff(
                        COMMIT_HASH,
                        null,
                        List.of(file),
                        2,
                        1,
                        file.patch()
                ),
                List.of()
        );

        assertTrue(completionService.finishIfNoWorkRemaining(task.getId()));
        return taskService.getById(task.getId());
    }

    private void moveToProcessing(Integer taskId) {
        assertTrue(taskService.updateStatusIfCurrent(
                taskId,
                AnalysisTaskStatus.NEW,
                AnalysisTaskStatus.CLONING
        ));
        assertTrue(taskService.updateStatusIfCurrent(
                taskId,
                AnalysisTaskStatus.CLONING,
                AnalysisTaskStatus.DISCOVERING_COMMITS
        ));
        assertTrue(taskService.updateStatusIfCurrent(
                taskId,
                AnalysisTaskStatus.DISCOVERING_COMMITS,
                AnalysisTaskStatus.PROCESSING
        ));
    }

    private AnalysisReportResponse waitForCompletion(
            Integer reportId,
            Duration timeout
    ) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        AnalysisReportResponse response;

        do {
            response = reportService.getResponse(reportId);
            if (response.status() == ReportStatus.COMPLETED
                    || response.status() == ReportStatus.FAILED) {
                return response;
            }
            Thread.sleep(20);
        } while (Instant.now().isBefore(deadline));

        throw new AssertionError(
                "Отчёт не завершился за " + timeout + ": " + response.status()
        );
    }

    private static Path createTestDatabasePath() {
        try {
            Path database = Files.createTempFile(
                    "repository-analyzer-report-test-",
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
