package com.ivan_naumenko.repository_analyzer.analysis.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.*;
import com.ivan_naumenko.repository_analyzer.analysis.orchestrator.AnalysisOrchestrator;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.commitinsight.repository.CommitInsightRepository;
import com.ivan_naumenko.repository_analyzer.insight.graph.service.CommitGraphService;
import com.ivan_naumenko.repository_analyzer.llm.cache.repository.LlmCacheRepository;
import com.ivan_naumenko.repository_analyzer.report.artifact.ReportEvidenceService;
import com.ivan_naumenko.repository_analyzer.report.dto.AnalysisReportResponse;
import com.ivan_naumenko.repository_analyzer.report.model.*;
import com.ivan_naumenko.repository_analyzer.report.orchestrator.ReportOrchestrator;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
import com.ivan_naumenko.repository_analyzer.report.service.ReportScopeService;
import com.ivan_naumenko.repository_analyzer.security.RepositoryUrlPolicy;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class IncrementalAnalysisIntegrationTest {
    private static final Path ROOT = tempRoot();
    @Autowired AnalysisTaskService tasks;
    @Autowired AnalysisOrchestrator analyses;
    @Autowired AnalysisRefreshService refresh;
    @Autowired AnalysisReportService reports;
    @Autowired ReportOrchestrator generation;
    @Autowired RepositoryCommitRepository commits;
    @Autowired CommitInsightRepository insights;
    @Autowired LlmCacheRepository cache;
    @Autowired ReportScopeService scopes;
    @Autowired ReportEvidenceService evidence;
    @Autowired CommitGraphService graph;
    @Autowired com.ivan_naumenko.repository_analyzer.llm.context.service.StoredCommitContextService contexts;
    @Autowired com.ivan_naumenko.repository_analyzer.llm.context.service.CompactContextService compact;
    @Autowired com.ivan_naumenko.repository_analyzer.diff.repository.CommitDiffRepository diffs;
    @Autowired com.ivan_naumenko.repository_analyzer.javaanalysis.repository.JavaFileAnalysisRepository javaFiles;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean RepositoryUrlPolicy urlPolicy;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:file:" + ROOT.resolve("test.db") + "?foreign_keys=on&journal_mode=WAL&busy_timeout=10000");
        registry.add("repository-analyzer.directory.path", () -> ROOT.resolve("clones").toString());
        registry.add("repository-analyzer.llm.provider", () -> "stub");
        registry.add("repository-analyzer.forge.enabled", () -> "false");
    }

    @Test void refreshProcessesOnlyNewCommitsAndPinsOldReportAndEvidence() throws Exception {
        try (Git origin = origin()) {
            commit(origin, "README.md", "# Payments\nInitial version\n");
            var task = analyze(origin);
            var old = generate(task.getId());
            var originalCommit = commits.findAllByAnalysisTask_Id(task.getId()).getFirst();
            var originalInsight = insights.findById(originalCommit.getId()).orElseThrow();
            long cachedBefore = cache.count();
            Files.writeString(origin.getRepository().getWorkTree().toPath().resolve("README.md"), "# Changed description\n");
            origin.add().addFilepattern("README.md").call();
            String newHash = commit(origin, "src/PaymentService.java", "class PaymentService {}\n");
            assertTrue(refresh.refresh(task.getId(), null));
            var updated = awaitTask(task.getId());
            assertEquals(task.getId(), updated.getId());
            assertEquals(2, updated.getRevision());
            assertEquals(1, updated.getAddedCommits());
            assertEquals(newHash, updated.getHeadCommitHash());
            assertEquals(1, commits.findById(originalCommit.getId()).orElseThrow().getAttemptCount());
            var latest = generate(task.getId());
            assertNotEquals(old.id(), latest.id());
            assertEquals(2, latest.totalCommits());
            assertEquals(1, reports.getResponse(old.id()).totalCommits());
            assertEquals(1, scopes.facts(old.id()).totalCommits());
            assertEquals(1, scopes.commitInsights(old.id()).size());
            assertEquals(originalInsight.getCreatedAt(), insights.findById(originalCommit.getId()).orElseThrow().getCreatedAt());
            assertEquals(cachedBefore + 2, cache.count()); // new insight and synthesis; source attachment needs no extra inference
            var source = reports.getResponse(old.id()).artifacts().evidence().stream().filter(e -> "COMMIT".equals(e.kind())).findFirst().orElseThrow();
            assertTrue(evidence.file(old.id(), source.id(), 0).patch().contains("Initial version"));
            var snapshot = old.artifacts().evidence().stream().filter(e -> "SNAPSHOT".equals(e.kind())).findFirst().orElseThrow();
            int readmePosition = snapshot.paths().indexOf("README.md");
            assertTrue(readmePosition >= 0);
            String oldReadme = evidence.file(old.id(), snapshot.id(), readmePosition).patch();
            assertTrue(oldReadme.contains("Initial version"));
            assertFalse(oldReadme.contains("Changed description"));
            var newCommit = commits.findAllByAnalysisTask_Id(task.getId()).stream().filter(c -> c.getCommitHash().equals(newHash)).findFirst().orElseThrow();
            assertThrows(com.ivan_naumenko.repository_analyzer.report.exception.ReportNotFoundException.class,
                    () -> evidence.detail(old.id(), "C" + newCommit.getId()));
        }
    }

    @Test void unchangedHeadReusesRevisionReportAndAllCachedResults() throws Exception {
        try (Git origin = origin()) {
            commit(origin, "README.md", "# Stable\n");
            var task = analyze(origin);
            var report = generate(task.getId());
            long cachedBefore = cache.count();
            assertTrue(refresh.refresh(task.getId(), null));
            var updated = awaitTask(task.getId());
            assertEquals(task.getRevision(), updated.getRevision());
            assertEquals(0, updated.getAddedCommits());
            assertEquals(report.id(), reports.createOrGet(task.getId(), ReportAudience.DEVELOPER).getId());
            assertEquals(cachedBefore, cache.count());
            assertEquals(1, commits.findAllByAnalysisTask_Id(task.getId()).getFirst().getAttemptCount());
        }
    }

    @Test void rewrittenBranchExcludesOrphansButKeepsHistoricalReportSources() throws Exception {
        try (Git origin = origin()) {
            String root = commit(origin, "README.md", "# Project\n");
            String removed = commit(origin, "obsolete.txt", "obsolete content\n");
            var task = analyze(origin);
            var old = generate(task.getId());
            origin.reset().setMode(ResetCommand.ResetType.HARD).setRef(root).call();
            commit(origin, "new.txt", "replacement\n");
            assertTrue(refresh.refresh(task.getId(), null));
            awaitTask(task.getId());
            assertEquals(2, graph.build(task.getId()).totalCommits());
            assertFalse(commits.findAllByAnalysisTask_Id(task.getId()).stream().anyMatch(c -> c.getCommitHash().equals(removed)));
            assertEquals(3, commits.findStoredByTask(task.getId()).size());
            assertTrue(scopes.commitInsights(old.id()).stream().anyMatch(c -> c.commitHash().equals(removed)));
            var source = old.artifacts().evidence().stream().filter(e -> removed.equals(e.commitHash()) && "COMMIT".equals(e.kind())).findFirst().orElseThrow();
            assertTrue(evidence.file(old.id(), source.id(), 0).patch().contains("obsolete content"));
            assertEquals(2, generate(task.getId()).totalCommits());
        }
    }

    @Test void bulkContextIsIdenticalWithConstantQueryCount() throws Exception {
        try (Git origin = origin()) {
            for (int i = 0; i < 12; i++) {
                Path file = origin.getRepository().getWorkTree().toPath().resolve("src/Sample" + i + ".java");
                Files.createDirectories(file.getParent());
                Files.writeString(file, "package sample; public class Sample" + i + " { public int size() { return 1; } static class Nested { void run() {} } }\n");
                origin.add().addFilepattern("src/Sample" + i + ".java").call();
            }
            commit(origin, "README.md", "# Bulk context benchmark\n");
            commit(origin, "src/Sample0.java", "package sample; public class Sample0 { public int size() { return 2; } }\n");
            var task = analyze(origin);
            var ids = commits.findAllByAnalysisTask_Id(task.getId()).stream().map(c -> c.getId()).toList();
            var old = new com.ivan_naumenko.repository_analyzer.evaluation.LegacyStoredCommitContextService(commits, diffs, javaFiles, compact);
            var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
            transaction.setReadOnly(true);
            var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            long started = System.nanoTime();
            var legacy = transaction.execute(status -> ids.stream().collect(java.util.stream.Collectors.toMap(id -> id, old::load)));
            long beforeMillis = (System.nanoTime() - started) / 1_000_000;
            long beforeQueries = statistics.getPrepareStatementCount();
            statistics.clear();
            started = System.nanoTime();
            var current = contexts.loadAll(ids);
            long afterMillis = (System.nanoTime() - started) / 1_000_000;
            long afterQueries = statistics.getPrepareStatementCount();
            statistics.setStatisticsEnabled(false);
            assertEquals(legacy, current, "Every patch, Java type/method, position and truncation flag must match");
            assertEquals(3, afterQueries, "One diff/files query, one Java/types query, one methods query per chunk");
            assertTrue(beforeQueries > afterQueries * 5);
            assertTrue(current.values().stream().flatMap(c -> c.files().stream()).anyMatch(f -> f.javaAnalysis() != null));
            Path output = Path.of("target", "performance");
            Files.createDirectories(output);
            Files.writeString(output.resolve("context.json"), "{\"commits\":" + ids.size() + ",\"beforeQueries\":" + beforeQueries
                    + ",\"afterQueries\":" + afterQueries + ",\"beforeMillis\":" + beforeMillis + ",\"afterMillis\":" + afterMillis + "}\n");
        }
    }

    @Test void mixedAndUnknownTextCodeProducesReportsAndPinnedSources() throws Exception {
        try (Git origin = origin()) {
            commit(origin, "pyproject.toml", "[project]\nname = 'payments'\n");
            commit(origin, "app/payments.py", "def debit(amount):\n    if amount == 0: raise ValueError('zero')\n    return amount\n");
            commit(origin, "src/auth.ts", "export function allowed(user: unknown) { return true; }\n");
            commit(origin, "rules/payment.custom", "rule refund allows any_user\n");
            commit(origin, "bin/charge", "#!/usr/bin/env python3\nprint('charge')\n");
            var task = analyze(origin);
            var report = generate(task.getId());
            assertEquals(5, report.totalCommits());
            assertEquals(5, scopes.commitInsights(report.id()).size());
            var snapshot = reports.snapshot(report.id());
            assertTrue(snapshot.languages().stream().anyMatch(l -> l.language().equals("Python")));
            assertTrue(snapshot.languages().stream().anyMatch(l -> l.language().equals("TypeScript")));
            assertTrue(snapshot.languages().stream().anyMatch(l -> l.language().equals("Text (.custom)")));
            for (String path : List.of("app/payments.py", "src/auth.ts", "rules/payment.custom", "bin/charge")) {
                var source = report.artifacts().evidence().stream().filter(e -> e.kind().equals("COMMIT") && e.paths().contains(path)).findFirst().orElseThrow();
                assertFalse(evidence.file(report.id(), source.id(), source.paths().indexOf(path)).patch().isBlank());
            }
            var summary = reports.getSummaries(task.getId()).getFirst();
            assertEquals(report.id(), summary.id());
            assertEquals(report.period(), summary.period());
            var serializer = tools.jackson.databind.json.JsonMapper.builder().build();
            assertFalse(serializer.writeValueAsString(summary).contains("\"artifacts\""));
            assertFalse(serializer.writeValueAsString(summary).contains("\"content\""));
        }
    }

    private AnalysisTask analyze(Git origin) throws Exception {
        when(urlPolicy.validate(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var task = tasks.createOrGet(origin.getRepository().getWorkTree().toURI().toString(), "main");
        assertTrue(analyses.start(task.getId()));
        return awaitTask(task.getId());
    }
    private AnalysisTask awaitTask(Integer id) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        do {
            var task = tasks.getById(id);
            if (task.getStatus() == AnalysisTaskStatus.COMPLETED) return task;
            if (task.getStatus() == AnalysisTaskStatus.FAILED) fail("Technical analysis failed");
            Thread.sleep(20);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("Analysis timeout");
    }
    private AnalysisReportResponse generate(Integer taskId) throws Exception {
        var report = reports.createOrGet(taskId, ReportAudience.DEVELOPER);
        assertTrue(generation.start(report.getId()));
        Instant deadline = Instant.now().plusSeconds(10);
        do {
            var response = reports.getResponse(report.getId());
            if (response.status() == ReportStatus.COMPLETED) return response;
            if (response.status() == ReportStatus.FAILED) fail(response.lastError());
            Thread.sleep(20);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("Report timeout");
    }
    private Git origin() throws Exception {
        return Git.init().setDirectory(Files.createTempDirectory(ROOT, "origin-").toFile()).setInitialBranch("main").call();
    }
    private String commit(Git git, String path, String text) throws Exception {
        Path file = git.getRepository().getWorkTree().toPath().resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        git.add().addFilepattern(path).call();
        return git.commit().setAuthor("Test", "test@example.com").setMessage("Update " + path).call().getName();
    }
    private static Path tempRoot() {
        try { return Files.createTempDirectory("incremental-analysis-test-"); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
}
