package com.ivan_naumenko.repository_analyzer.report.orchestrator;

import com.ivan_naumenko.repository_analyzer.commitinsight.dto.CommitInsightResponse;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.ChangeCategory;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDetails;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDraft;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightStatus;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.InsightWorkUnit;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.RiskLevel;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightGenerator;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightPlanner;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightService;
import com.ivan_naumenko.repository_analyzer.diff.model.ChangedFileRow;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.diff.model.FileChangeType;
import com.ivan_naumenko.repository_analyzer.diff.service.TaskChangeService;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.report.config.ReportProperties;
import com.ivan_naumenko.repository_analyzer.report.dto.AnalysisReportResponse;
import com.ivan_naumenko.repository_analyzer.report.dto.ReportPeriodResponse;
import com.ivan_naumenko.repository_analyzer.report.facts.service.RepositoryFactsService;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriod;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriodType;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
import com.ivan_naumenko.repository_analyzer.report.service.ProjectSynthesisService;
import com.ivan_naumenko.repository_analyzer.report.service.ReportPeriodResolver;
import com.ivan_naumenko.repository_analyzer.report.sse.ReportEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportOrchestratorConcurrencyTest {

    private static final Instant LATEST = Instant.parse("2026-07-20T10:00:00Z");

    @Mock
    private AnalysisReportService reportService;

    @Mock
    private TaskChangeService changeService;

    @Mock
    private CommitInsightService insightService;

    @Mock
    private CommitInsightGenerator generator;

    @Mock
    private RepositoryFactsService factsService;

    @Mock
    private ProjectSynthesisService synthesisService;

    @Mock
    private ReportEventService eventService;

    @Mock
    private GitService gitService;

    @Test
    void analyzesUnitsConcurrentlyAndSynthesizesOnce() throws Exception {
        int reportId = 77;
        int taskId = 12;
        List<CommitChangeSummary> commits = commits(6, Duration.ZERO);
        ReportGenerationData generationData = generationData(reportId, taskId, ReportPeriod.all());

        when(reportService.claim(reportId)).thenReturn(true);
        when(reportService.getGenerationData(reportId)).thenReturn(generationData);
        when(reportService.loadCommits(reportId)).thenReturn(commits);
        when(insightService.findReusableCommitIds(taskId)).thenReturn(Set.of());
        when(insightService.toResponse(any(), any())).thenReturn(insightResponse());

        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        CountDownLatch firstWave = new CountDownLatch(3);
        when(generator.generate(any(InsightWorkUnit.class), anyString())).thenAnswer(invocation -> {
            InsightWorkUnit unit = invocation.getArgument(0);
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            firstWave.countDown();
            try {
                assertTrue(firstWave.await(3, TimeUnit.SECONDS));
                Thread.sleep(20);
                return Map.of(unit.commits().getFirst().commitId(), draft());
            } finally {
                active.decrementAndGet();
            }
        });

        List<Integer> progressUpdates = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            progressUpdates.add(invocation.getArgument(1));
            return null;
        }).when(reportService).updateProgress(eq(reportId), anyInt());
        when(insightService.findForCommits(eq(taskId), anyList())).thenReturn(List.of());
        when(synthesisService.synthesize(eq(generationData), any(), anyList()))
                .thenReturn(new com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.SynthesisResult("## Главное\n\n- Итог.", null));

        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> finalContent = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        doAnswer(invocation -> {
            finalContent.set(((com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.SynthesisResult) invocation.getArgument(1)).content());
            completed.countDown();
            return null;
        }).when(reportService).complete(eq(reportId), any(com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.SynthesisResult.class), any());
        lenient().doAnswer(invocation -> {
            failure.set(invocation.getArgument(1));
            completed.countDown();
            return true;
        }).when(reportService).fail(eq(reportId), any(Throwable.class));

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            assertTrue(orchestrator(executor, 3).start(reportId));
            assertTrue(completed.await(5, TimeUnit.SECONDS));
        }

        assertNull(failure.get());
        assertEquals(3, maximumActive.get());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), progressUpdates);
        assertEquals("## Главное\n\n- Итог.", finalContent.get());
        verify(insightService, times(6)).saveAll(org.mockito.ArgumentMatchers.anyMap());
        verify(synthesisService, times(1)).synthesize(eq(generationData), any(), anyList());
    }

    @Test
    void reusesExistingInsightsAndSkipsLlmWork() throws Exception {
        int reportId = 79;
        int taskId = 14;
        List<CommitChangeSummary> commits = commits(3, Duration.ZERO);
        ReportGenerationData generationData = generationData(reportId, taskId, ReportPeriod.all());

        when(reportService.claim(reportId)).thenReturn(true);
        when(reportService.getGenerationData(reportId)).thenReturn(generationData);
        when(reportService.loadCommits(reportId)).thenReturn(commits);
        when(insightService.findReusableCommitIds(taskId)).thenReturn(Set.of(10, 20, 30));
        when(insightService.findForCommits(eq(taskId), anyList())).thenReturn(List.of());
        when(synthesisService.synthesize(eq(generationData), any(), anyList()))
                .thenReturn(new com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.SynthesisResult("## Главное", null));

        CountDownLatch completed = new CountDownLatch(1);
        doAnswer(invocation -> {
            completed.countDown();
            return null;
        }).when(reportService).complete(eq(reportId), any(com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.SynthesisResult.class), any());

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            assertTrue(orchestrator(executor, 2).start(reportId));
            assertTrue(completed.await(5, TimeUnit.SECONDS));
        }

        verify(generator, never()).generate(any(), anyString());
        verify(reportService).updateProgress(reportId, 3);
    }

    @Test
    void limitsReportToSelectedPeriod() throws Exception {
        int reportId = 81;
        int taskId = 15;
        // Коммиты раз в 20 дней: в последние 30 дней попадают только два самых новых.
        List<CommitChangeSummary> commits = commits(5, Duration.ofDays(20));
        ReportGenerationData generationData = generationData(
                reportId,
                taskId,
                new ReportPeriod(ReportPeriodType.LAST_DAYS, 30, null, null)
        );

        when(reportService.claim(reportId)).thenReturn(true);
        when(reportService.getGenerationData(reportId)).thenReturn(generationData);
        when(reportService.loadCommits(reportId)).thenReturn(List.of(commits.get(3), commits.get(4)));
        when(insightService.findReusableCommitIds(taskId)).thenReturn(Set.of(10, 20, 30, 40, 50));
        when(insightService.findForCommits(eq(taskId), anyList())).thenReturn(List.of());
        when(synthesisService.synthesize(eq(generationData), any(), anyList()))
                .thenReturn(new com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.SynthesisResult("## Главное", null));

        CountDownLatch completed = new CountDownLatch(1);
        doAnswer(invocation -> {
            completed.countDown();
            return null;
        }).when(reportService).complete(eq(reportId), any(com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.SynthesisResult.class), any());

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            assertTrue(orchestrator(executor, 2).start(reportId));
            assertTrue(completed.await(5, TimeUnit.SECONDS));
        }

        verify(reportService).initializeProgress(reportId, 2);
        verify(factsService).build(eq(taskId), eq(List.of(commits.get(3), commits.get(4))));
    }

    @Test
    void cancellationInterruptsRunningUnits() throws Exception {
        int reportId = 88;
        int taskId = 13;
        List<CommitChangeSummary> commits = commits(5, Duration.ZERO);
        ReportGenerationData generationData = generationData(reportId, taskId, ReportPeriod.all());

        when(reportService.claim(reportId)).thenReturn(true);
        when(reportService.getGenerationData(reportId)).thenReturn(generationData);
        when(reportService.loadCommits(reportId)).thenReturn(commits);
        when(insightService.findReusableCommitIds(taskId)).thenReturn(Set.of());

        CountDownLatch requestsStarted = new CountDownLatch(2);
        CountDownLatch requestsFinished = new CountDownLatch(2);
        AtomicInteger requestCount = new AtomicInteger();
        AtomicInteger interrupted = new AtomicInteger();
        when(generator.generate(any(InsightWorkUnit.class), anyString())).thenAnswer(invocation -> {
            requestCount.incrementAndGet();
            requestsStarted.countDown();
            try {
                Thread.sleep(10_000);
                throw new AssertionError("Запрос не был прерван");
            } catch (InterruptedException exception) {
                interrupted.incrementAndGet();
                throw new CancellationException("остановлено");
            } finally {
                requestsFinished.countDown();
            }
        });
        when(reportService.cancel(reportId)).thenReturn(true);
        when(reportService.getResponse(reportId)).thenReturn(
                new AnalysisReportResponse(
                        reportId,
                        taskId,
                        "https://example.com/cancel.git",
                        "main",
                        ReportPeriodResponse.from(ReportPeriod.all()),
                        ReportAudience.DEVELOPER,
                        ReportStatus.CANCELLED,
                        commits.size(),
                        0,
                        null,
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )
        );

        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            ReportOrchestrator orchestrator = orchestrator(executor, 2);

            assertTrue(orchestrator.start(reportId));
            assertTrue(requestsStarted.await(3, TimeUnit.SECONDS));
            long cancelledAt = System.nanoTime();
            assertTrue(orchestrator.cancel(reportId));
            assertTrue(requestsFinished.await(3, TimeUnit.SECONDS));
            assertTrue(Duration.ofNanos(System.nanoTime() - cancelledAt).toMillis() < 2_000);
        }

        assertEquals(2, requestCount.get());
        assertEquals(2, interrupted.get());
        verify(reportService, never()).complete(eq(reportId), any(com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.SynthesisResult.class), any());
        verify(reportService, never()).fail(eq(reportId), any(Throwable.class));
        verify(synthesisService, never()).synthesize(any(), any(), anyList());
        verify(insightService, never()).saveAll(org.mockito.ArgumentMatchers.anyMap());
        verify(eventService).complete(reportId);
    }

    private ReportOrchestrator orchestrator(ExecutorService executor, int maxInFlight) {
        ReportProperties properties = new ReportProperties(maxInFlight, 4_000, 2, 1, 40);
        return new ReportOrchestrator(
                reportService,
                changeService,
                new ReportPeriodResolver(gitService),
                insightService,
                new CommitInsightPlanner(properties),
                generator,
                factsService,
                synthesisService,
                eventService,
                properties,
                executor
        );
    }

    private ReportGenerationData generationData(int reportId, int taskId, ReportPeriod period) {
        return new ReportGenerationData(
                reportId,
                taskId,
                ReportAudience.DEVELOPER,
                "https://example.com/concurrent.git",
                "main",
                period
        );
    }

    /** Коммиты от старых к новым; самый новый датирован LATEST. */
    private List<CommitChangeSummary> commits(int count, Duration step) {
        return IntStream.range(0, count)
                .mapToObj(position -> {
                    int commitId = (position + 1) * 10;
                    return new CommitChangeSummary(
                            position,
                            commitId,
                            "%040d".formatted(commitId),
                            "Commit " + commitId,
                            "Test User",
                            LATEST.minus(step.multipliedBy(count - 1L - position)),
                            100,
                            20,
                            List.of(new ChangedFileRow(
                                    commitId,
                                    "src/App.java",
                                    "src/App.java",
                                    FileChangeType.MODIFY,
                                    100,
                                    20
                            )),
                            1
                    );
                })
                .toList();
    }

    private CommitInsightResponse insightResponse() {
        return new CommitInsightResponse(
                10,
                0,
                "%040d".formatted(10),
                "Commit",
                "Test User",
                LATEST,
                1,
                0,
                1,
                CommitInsightStatus.ANALYZED,
                ChangeCategory.FEATURE,
                ChangeCategory.FEATURE.displayName(),
                RiskLevel.LOW,
                "Результат",
                CommitInsightDetails.empty()
        );
    }

    private CommitInsightDraft draft() {
        return new CommitInsightDraft(
                CommitInsightStatus.ANALYZED,
                ChangeCategory.FEATURE,
                RiskLevel.LOW,
                "Результат",
                CommitInsightDetails.empty()
        );
    }
}
