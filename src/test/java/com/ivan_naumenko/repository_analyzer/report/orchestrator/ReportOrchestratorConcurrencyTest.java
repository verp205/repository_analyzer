package com.ivan_naumenko.repository_analyzer.report.orchestrator;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.CommitClustersResponse;
import com.ivan_naumenko.repository_analyzer.insight.cluster.service.CommitClusteringService;
import com.ivan_naumenko.repository_analyzer.insight.graph.dto.CommitGraphResponse;
import com.ivan_naumenko.repository_analyzer.insight.graph.service.CommitGraphService;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactCommitContext;
import com.ivan_naumenko.repository_analyzer.llm.context.service.StoredCommitContextService;
import com.ivan_naumenko.repository_analyzer.llm.coordinator.LlmRequestCoordinator;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.llm.prompt.service.PromptFactory;
import com.ivan_naumenko.repository_analyzer.report.config.ReportProperties;
import com.ivan_naumenko.repository_analyzer.report.dto.AnalysisReportResponse;
import com.ivan_naumenko.repository_analyzer.report.model.CommitReportResult;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
import com.ivan_naumenko.repository_analyzer.report.service.ProjectSynthesisService;
import com.ivan_naumenko.repository_analyzer.report.service.ReportTextSanitizer;
import com.ivan_naumenko.repository_analyzer.report.sse.ReportEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportOrchestratorConcurrencyTest {

    @Mock
    private AnalysisReportService reportService;

    @Mock
    private RepositoryCommitRepository commitRepository;

    @Mock
    private StoredCommitContextService contextService;

    @Mock
    private PromptFactory promptFactory;

    @Mock
    private LlmRequestCoordinator requestCoordinator;

    @Mock
    private CommitGraphService graphService;

    @Mock
    private CommitClusteringService clusteringService;

    @Mock
    private ProjectSynthesisService synthesisService;

    @Mock
    private ReportEventService eventService;

    @Test
    void processesCommitsConcurrentlyButSynthesizesInOriginalOrder()
            throws Exception {
        int reportId = 77;
        int taskId = 12;
        List<Integer> commitIds = List.of(10, 20, 30, 40, 50, 60);
        ReportGenerationData generationData = new ReportGenerationData(
                reportId,
                taskId,
                ReportAudience.DEVELOPER,
                "https://example.com/concurrent.git",
                "main"
        );
        CommitGraphResponse graph = new CommitGraphResponse(
                taskId,
                commitIds.size(),
                1,
                1,
                0,
                commitIds.size(),
                0,
                List.of(),
                List.of()
        );
        CommitClustersResponse clusters = new CommitClustersResponse(
                taskId,
                0,
                List.of()
        );

        when(reportService.claim(reportId)).thenReturn(true);
        when(reportService.getGenerationData(reportId))
                .thenReturn(generationData);
        when(commitRepository.findIdsForReport(
                taskId,
                CommitProcessingStatus.COMPLETED
        )).thenReturn(commitIds);
        when(graphService.build(taskId)).thenReturn(graph);
        when(clusteringService.cluster(taskId)).thenReturn(clusters);
        when(contextService.load(anyInt())).thenAnswer(invocation -> {
            int commitId = invocation.getArgument(0);
            return context(commitId);
        });
        when(promptFactory.build(
                any(CompactCommitContext.class),
                eq(ReportAudience.DEVELOPER)
        )).thenAnswer(invocation -> {
            CompactCommitContext context = invocation.getArgument(0);
            return "prompt-" + context.commitHash();
        });

        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        CountDownLatch firstWave = new CountDownLatch(3);
        when(requestCoordinator.getOrGenerate(
                any(String.class),
                eq(ReportAudience.DEVELOPER),
                eq(LlmRequestProfile.COMMIT_ANALYSIS)
        )).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0);
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            firstWave.countDown();
            try {
                assertTrue(firstWave.await(3, TimeUnit.SECONDS));
                int commitId = Integer.parseInt(
                        prompt.substring(prompt.length() - 6)
                );
                Thread.sleep((70 - commitId) * 2L);
                return "Результат " + commitId;
            } finally {
                active.decrementAndGet();
            }
        });

        AtomicReference<List<CommitReportResult>> synthesisInput =
                new AtomicReference<>();
        List<Integer> progressUpdates = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            progressUpdates.add(invocation.getArgument(1));
            return null;
        }).when(reportService).updateProgress(
                eq(reportId),
                anyInt(),
                anyString()
        );
        when(synthesisService.synthesize(
                eq(generationData),
                eq(graph),
                eq(clusters),
                anyList()
        )).thenAnswer(invocation -> {
            synthesisInput.set(List.copyOf(invocation.getArgument(3)));
            return "## 1. Общая картина проекта\n\nИтог.";
        });

        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> finalContent = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        doAnswer(invocation -> {
            finalContent.set(invocation.getArgument(1));
            completed.countDown();
            return null;
        }).when(reportService).complete(eq(reportId), any(String.class));
        lenient().doAnswer(invocation -> {
            failure.set(invocation.getArgument(1));
            completed.countDown();
            return true;
        }).when(reportService).fail(eq(reportId), any(Throwable.class));

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            ReportOrchestrator orchestrator = new ReportOrchestrator(
                    reportService,
                    commitRepository,
                    contextService,
                    promptFactory,
                    requestCoordinator,
                    graphService,
                    clusteringService,
                    synthesisService,
                    new ReportTextSanitizer(),
                    eventService,
                    new ReportProperties(3, 4_000, 2),
                    executor
            );

            assertTrue(orchestrator.start(reportId));
            assertTrue(completed.await(5, TimeUnit.SECONDS));
        }

        assertNull(failure.get());
        assertEquals(3, maximumActive.get());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), progressUpdates);
        assertEquals(
                List.of(0, 1, 2, 3, 4, 5),
                synthesisInput.get().stream()
                        .map(CommitReportResult::position)
                        .toList()
        );

        String content = finalContent.get();
        int previousIndex = -1;
        for (int commitId : commitIds) {
            int currentIndex = content.indexOf(hash(commitId));
            assertTrue(currentIndex > previousIndex);
            previousIndex = currentIndex;
        }
    }

    @Test
    void cancellationStopsSubmittingNewCommitJobs() throws Exception {
        int reportId = 88;
        int taskId = 13;
        List<Integer> commitIds = List.of(10, 20, 30, 40, 50);
        ReportGenerationData generationData = new ReportGenerationData(
                reportId,
                taskId,
                ReportAudience.DEVELOPER,
                "https://example.com/cancel.git",
                "main"
        );
        CommitGraphResponse graph = new CommitGraphResponse(
                taskId,
                commitIds.size(),
                1,
                1,
                0,
                commitIds.size(),
                0,
                List.of(),
                List.of()
        );
        CommitClustersResponse clusters = new CommitClustersResponse(
                taskId,
                0,
                List.of()
        );

        when(reportService.claim(reportId)).thenReturn(true);
        when(reportService.getGenerationData(reportId))
                .thenReturn(generationData);
        when(commitRepository.findIdsForReport(
                taskId,
                CommitProcessingStatus.COMPLETED
        )).thenReturn(commitIds);
        when(graphService.build(taskId)).thenReturn(graph);
        when(clusteringService.cluster(taskId)).thenReturn(clusters);
        when(contextService.load(anyInt())).thenAnswer(invocation ->
                context(invocation.getArgument(0))
        );
        when(promptFactory.build(
                any(CompactCommitContext.class),
                eq(ReportAudience.DEVELOPER)
        )).thenReturn("prompt");

        CountDownLatch requestsStarted = new CountDownLatch(2);
        CountDownLatch releaseRequests = new CountDownLatch(1);
        CountDownLatch requestsFinished = new CountDownLatch(2);
        AtomicInteger requestCount = new AtomicInteger();
        when(requestCoordinator.getOrGenerate(
                any(String.class),
                eq(ReportAudience.DEVELOPER),
                eq(LlmRequestProfile.COMMIT_ANALYSIS)
        )).thenAnswer(invocation -> {
            requestCount.incrementAndGet();
            requestsStarted.countDown();
            try {
                assertTrue(releaseRequests.await(3, TimeUnit.SECONDS));
                return "Результат";
            } finally {
                requestsFinished.countDown();
            }
        });
        when(reportService.cancel(reportId)).thenReturn(true);
        when(reportService.getResponse(reportId)).thenReturn(
                new AnalysisReportResponse(
                        reportId,
                        taskId,
                        ReportAudience.DEVELOPER,
                        ReportStatus.CANCELLED,
                        commitIds.size(),
                        0,
                        null,
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )
        );

        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            ReportOrchestrator orchestrator = new ReportOrchestrator(
                    reportService,
                    commitRepository,
                    contextService,
                    promptFactory,
                    requestCoordinator,
                    graphService,
                    clusteringService,
                    synthesisService,
                    new ReportTextSanitizer(),
                    eventService,
                    new ReportProperties(2, 4_000, 2),
                    executor
            );

            assertTrue(orchestrator.start(reportId));
            assertTrue(requestsStarted.await(3, TimeUnit.SECONDS));
            assertTrue(orchestrator.cancel(reportId));
            releaseRequests.countDown();
            assertTrue(requestsFinished.await(3, TimeUnit.SECONDS));
        }

        assertEquals(2, requestCount.get());
        verify(reportService, never()).complete(eq(reportId), anyString());
        verify(reportService, never()).fail(eq(reportId), any(Throwable.class));
        verify(synthesisService, never()).synthesize(
                any(),
                any(),
                any(),
                anyList()
        );
        verify(eventService).complete(reportId);
    }

    private CompactCommitContext context(int commitId) {
        return new CompactCommitContext(
                hash(commitId),
                null,
                "Commit " + commitId,
                "Test User",
                Instant.parse("2026-07-20T10:00:00Z"),
                1,
                0,
                List.of(),
                0
        );
    }

    private String hash(int commitId) {
        return "0".repeat(34) + "%06d".formatted(commitId);
    }
}
