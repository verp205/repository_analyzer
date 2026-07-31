package com.ivan_naumenko.repository_analyzer.report.orchestrator;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.CommitClustersResponse;
import com.ivan_naumenko.repository_analyzer.insight.cluster.service.CommitClusteringService;
import com.ivan_naumenko.repository_analyzer.insight.graph.dto.CommitGraphResponse;
import com.ivan_naumenko.repository_analyzer.insight.graph.service.CommitGraphService;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactCommitContext;
import com.ivan_naumenko.repository_analyzer.llm.context.service.StoredCommitContextService;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.llm.coordinator.LlmRequestCoordinator;
import com.ivan_naumenko.repository_analyzer.llm.prompt.service.PromptFactory;
import com.ivan_naumenko.repository_analyzer.report.config.ReportProperties;
import com.ivan_naumenko.repository_analyzer.report.dto.AnalysisReportResponse;
import com.ivan_naumenko.repository_analyzer.report.model.CommitReportResult;
import com.ivan_naumenko.repository_analyzer.report.model.ReportCommitJob;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
import com.ivan_naumenko.repository_analyzer.report.service.ProjectSynthesisService;
import com.ivan_naumenko.repository_analyzer.report.service.ReportTextSanitizer;
import com.ivan_naumenko.repository_analyzer.report.sse.ReportEvent;
import com.ivan_naumenko.repository_analyzer.report.sse.ReportEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ReportOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(
            ReportOrchestrator.class
    );

    private final AnalysisReportService reportService;
    private final RepositoryCommitRepository commitRepository;
    private final StoredCommitContextService contextService;
    private final PromptFactory promptFactory;
    private final LlmRequestCoordinator requestCoordinator;
    private final CommitGraphService graphService;
    private final CommitClusteringService clusteringService;
    private final ProjectSynthesisService synthesisService;
    private final ReportTextSanitizer sanitizer;
    private final ReportEventService eventService;
    private final ReportProperties properties;
    private final ExecutorService executor;
    private final Map<Integer, GenerationSession> activeSessions =
            new ConcurrentHashMap<>();

    public ReportOrchestrator(
            AnalysisReportService reportService,
            RepositoryCommitRepository commitRepository,
            StoredCommitContextService contextService,
            PromptFactory promptFactory,
            LlmRequestCoordinator requestCoordinator,
            CommitGraphService graphService,
            CommitClusteringService clusteringService,
            ProjectSynthesisService synthesisService,
            ReportTextSanitizer sanitizer,
            ReportEventService eventService,
            ReportProperties properties,
            @Qualifier("analysisExecutor") ExecutorService executor
    ) {
        this.reportService = reportService;
        this.commitRepository = commitRepository;
        this.contextService = contextService;
        this.promptFactory = promptFactory;
        this.requestCoordinator = requestCoordinator;
        this.graphService = graphService;
        this.clusteringService = clusteringService;
        this.synthesisService = synthesisService;
        this.sanitizer = sanitizer;
        this.eventService = eventService;
        this.properties = properties;
        this.executor = executor;
    }

    public boolean start(Integer reportId) {
        Objects.requireNonNull(reportId, "reportId не должен быть null");
        if (!reportService.claim(reportId)) {
            return false;
        }

        try {
            executor.execute(() -> prepare(reportId));
            return true;
        } catch (RejectedExecutionException exception) {
            reportService.returnToNew(reportId);
            throw exception;
        }
    }

    public boolean cancel(Integer reportId) {
        Objects.requireNonNull(reportId, "reportId не должен быть null");
        if (!reportService.cancel(reportId)) {
            return false;
        }

        GenerationSession session = activeSessions.remove(reportId);
        if (session != null) {
            session.stopForCancellation();
        }
        AnalysisReportResponse current = reportService.getResponse(reportId);
        publish(
                "CANCELLED",
                reportId,
                current.processedCommits(),
                current.totalCommits(),
                "Формирование отчёта остановлено пользователем",
                null
        );
        eventService.complete(reportId);
        return true;
    }

    private void prepare(Integer reportId) {
        try {
            ReportGenerationData report = reportService.getGenerationData(
                    reportId
            );
            List<Integer> commitIds = commitRepository.findIdsForReport(
                    report.taskId(),
                    CommitProcessingStatus.COMPLETED
            );
            List<ReportCommitJob> jobs = new ArrayList<>(commitIds.size());
            for (int position = 0; position < commitIds.size(); position++) {
                jobs.add(new ReportCommitJob(position, commitIds.get(position)));
            }

            reportService.initializeProgress(reportId, jobs.size());
            CommitGraphResponse graph = graphService.build(report.taskId());
            CommitClustersResponse clusters = clusteringService.cluster(
                    report.taskId()
            );
            String draftHeader = buildDraftHeader(
                    report,
                    graph,
                    clusters
            );
            reportService.updateProgress(reportId, 0, draftHeader);
            publish(
                    "STARTED",
                    reportId,
                    0,
                    jobs.size(),
                    "Параллельный анализ коммитов начат",
                    null
            );
            publish(
                    "SUMMARY",
                    reportId,
                    0,
                    jobs.size(),
                    "Техническая сводка сформирована",
                    draftHeader
            );

            GenerationSession session = new GenerationSession(
                    report,
                    graph,
                    clusters,
                    jobs,
                    draftHeader
            );
            GenerationSession existing = activeSessions.putIfAbsent(
                    reportId,
                    session
            );
            if (existing != null) {
                throw new IllegalStateException(
                        "Отчёт уже формируется: " + reportId
                );
            }
            if (reportService.isCancelled(reportId)) {
                session.stopForCancellation();
                return;
            }
            session.start();
        } catch (Throwable exception) {
            if (!reportService.isCancelled(reportId)) {
                failReport(reportId, 0, 0, exception);
            }
        }
    }

    private CommitReportResult processCommit(
            ReportGenerationData report,
            ReportCommitJob job
    ) {
        log.info(
                "Отчёт {}: начат LLM-анализ коммита {}, позиция {}",
                report.reportId(),
                job.commitId(),
                job.position()
        );
        CompactCommitContext context = contextService.load(job.commitId());
        String prompt = promptFactory.build(context, report.audience());
        String response = sanitizer.sanitize(
                requestCoordinator.getOrGenerate(
                        prompt,
                        report.audience(),
                        LlmRequestProfile.COMMIT_ANALYSIS
                )
        );
        String message = oneLine(context.message(), "Без сообщения");
        String commitSection = """
                ### %s — %s

                %s

                """.formatted(context.commitHash(), message, response);

        log.info(
                "Отчёт {}: завершён LLM-анализ коммита {}, позиция {}",
                report.reportId(),
                job.commitId(),
                job.position()
        );

        return new CommitReportResult(
                job.position(),
                job.commitId(),
                context.commitHash(),
                message,
                commitSection
        );
    }

    private String buildDraftHeader(
            ReportGenerationData report,
            CommitGraphResponse graph,
            CommitClustersResponse clusters
    ) {
        StringBuilder content = new StringBuilder()
                .append("# Анализ истории репозитория\n\n")
                .append("Репозиторий: ")
                .append(report.repositoryUrl())
                .append("\n\nВетка: ")
                .append(report.branch())
                .append("\n\nАудитория: ")
                .append(report.audience().displayName())
                .append("\n\n")
                .append("> Коммиты анализируются параллельно. Общая картина ")
                .append("проекта появится после их смыслового синтеза.\n\n")
                .append("## Техническая сводка\n\n")
                .append("- Коммитов: ")
                .append(graph.totalCommits())
                .append("\n- Корней графа: ")
                .append(graph.rootCount())
                .append("\n- Вершин без потомков: ")
                .append(graph.tipCount())
                .append("\n- Merge-коммитов: ")
                .append(graph.mergeCommitCount())
                .append("\n- Максимальная глубина: ")
                .append(graph.maxDepth())
                .append("\n\n")
                .append("## Группы изменений\n\n");

        if (clusters.clusters().isEmpty()) {
            content.append("Изменения для кластеризации отсутствуют.\n\n");
        } else {
            clusters.clusters().forEach(cluster -> content
                    .append("- ")
                    .append(cluster.displayName())
                    .append(": ")
                    .append(cluster.commits().size())
                    .append(" коммитов\n"));
            content.append("\n");
        }
        content.append("## Анализ отдельных коммитов\n\n");
        return content.toString();
    }

    private String buildFinalContent(
            ReportGenerationData report,
            String synthesis,
            List<CommitReportResult> orderedResults
    ) {
        StringBuilder content = new StringBuilder()
                .append("# Итоговый отчёт по репозиторию\n\n")
                .append("Репозиторий: ")
                .append(report.repositoryUrl())
                .append("\n\nВетка: ")
                .append(report.branch())
                .append("\n\nАудитория: ")
                .append(report.audience().displayName())
                .append("\n\n")
                .append(synthesis)
                .append("\n\n## 10. Приложение: анализ отдельных коммитов\n\n");

        if (orderedResults.isEmpty()) {
            content.append("Завершённые коммиты для анализа отсутствуют.\n");
        } else {
            orderedResults.forEach(result -> content.append(result.content()));
        }
        return content.toString();
    }

    private String oneLine(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replaceAll("\\R+", " ").strip();
    }

    private void failReport(
            Integer reportId,
            int processed,
            int total,
            Throwable exception
    ) {
        activeSessions.remove(reportId);
        Throwable cause = unwrap(exception);
        try {
            reportService.fail(reportId, cause);
            publish(
                    "FAILED",
                    reportId,
                    processed,
                    total,
                    Objects.toString(cause.getMessage(), "Неизвестная ошибка"),
                    null
            );
        } finally {
            eventService.complete(reportId);
            log.error(
                    "Формирование отчёта {} завершилось ошибкой",
                    reportId,
                    cause
            );
        }
    }

    private Throwable unwrap(Throwable exception) {
        if (exception instanceof CompletionException completionException
                && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return exception;
    }

    private void publish(
            String type,
            Integer reportId,
            int processed,
            int total,
            String message,
            String contentChunk
    ) {
        eventService.publish(new ReportEvent(
                type,
                reportId,
                processed,
                total,
                message,
                contentChunk,
                LocalDateTime.now()
        ));
    }

    private final class GenerationSession {

        private final ReportGenerationData report;
        private final CommitGraphResponse graph;
        private final CommitClustersResponse clusters;
        private final List<ReportCommitJob> jobs;
        private final String draftHeader;
        private final Map<Integer, CommitReportResult> readyResults =
                new ConcurrentHashMap<>();
        private final List<CommitReportResult> orderedResults =
                new ArrayList<>();
        private final StringBuilder appendix = new StringBuilder();
        private final AtomicInteger nextJobIndex = new AtomicInteger();
        private final AtomicInteger completedJobs = new AtomicInteger();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final AtomicBoolean synthesisStarted = new AtomicBoolean();
        private final Object assemblyMonitor = new Object();
        private volatile int nextExpectedPosition;

        private GenerationSession(
                ReportGenerationData report,
                CommitGraphResponse graph,
                CommitClustersResponse clusters,
                List<ReportCommitJob> jobs,
                String draftHeader
        ) {
            this.report = report;
            this.graph = graph;
            this.clusters = clusters;
            this.jobs = List.copyOf(jobs);
            this.draftHeader = draftHeader;
        }

        private void start() {
            if (jobs.isEmpty()) {
                beginSynthesis();
                return;
            }
            int initialTasks = Math.min(
                    properties.maxInFlight(),
                    jobs.size()
            );
            for (int index = 0; index < initialTasks; index++) {
                submitNext();
            }
        }

        private void submitNext() {
            if (stopped.get()) {
                return;
            }
            int jobIndex = nextJobIndex.getAndIncrement();
            if (jobIndex >= jobs.size()) {
                return;
            }

            ReportCommitJob job = jobs.get(jobIndex);
            try {
                CompletableFuture
                        .supplyAsync(
                                () -> processCommit(report, job),
                                executor
                        )
                        .whenComplete(this::handleCompletion);
            } catch (Throwable exception) {
                fail(exception);
            }
        }

        private void handleCompletion(
                CommitReportResult result,
                Throwable exception
        ) {
            if (exception != null) {
                fail(exception);
                return;
            }
            try {
                accept(result);
            } catch (Throwable callbackFailure) {
                fail(callbackFailure);
            }
        }

        private void accept(CommitReportResult result) {
            if (stopped.get()) {
                return;
            }
            CommitReportResult duplicate = readyResults.putIfAbsent(
                    result.position(),
                    result
            );
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Позиция отчёта обработана повторно: "
                                + result.position()
                );
            }

            boolean shouldSynthesize;
            int completed;
            String contentChunk;
            synchronized (assemblyMonitor) {
                if (stopped.get()) {
                    return;
                }
                completed = completedJobs.incrementAndGet();
                StringBuilder newlyAssembled = new StringBuilder();
                CommitReportResult next;
                while ((next = readyResults.remove(
                        nextExpectedPosition
                )) != null) {
                    orderedResults.add(next);
                    appendix.append(next.content());
                    newlyAssembled.append(next.content());
                    nextExpectedPosition++;
                }

                reportService.updateProgress(
                        report.reportId(),
                        completed,
                        draftHeader + appendix
                );
                contentChunk = newlyAssembled.isEmpty()
                        ? null
                        : newlyAssembled.toString();
                shouldSynthesize = completed == jobs.size()
                        && nextExpectedPosition == jobs.size()
                        && synthesisStarted.compareAndSet(false, true);
                publish(
                        "PROGRESS",
                        report.reportId(),
                        completed,
                        jobs.size(),
                        "LLM-анализ коммита завершён",
                        contentChunk
                );
            }

            submitNext();
            if (shouldSynthesize) {
                beginSynthesis();
            }
        }

        private void beginSynthesis() {
            if (stopped.get()) {
                return;
            }
            if (!jobs.isEmpty()
                    && !synthesisStarted.get()) {
                return;
            }
            if (jobs.isEmpty()
                    && !synthesisStarted.compareAndSet(false, true)) {
                return;
            }

            try {
                publish(
                        "PROGRESS",
                        report.reportId(),
                        jobs.size(),
                        jobs.size(),
                        "Коммиты обработаны, формируется общая картина проекта",
                        null
                );
                String synthesis = synthesisService.synthesize(
                        report,
                        graph,
                        clusters,
                        List.copyOf(orderedResults)
                );
                String finalContent = buildFinalContent(
                        report,
                        synthesis,
                        orderedResults
                );
                reportService.complete(report.reportId(), finalContent);
                stopped.set(true);
                activeSessions.remove(report.reportId(), this);
                publish(
                        "COMPLETED",
                        report.reportId(),
                        jobs.size(),
                        jobs.size(),
                        "Общий отчёт сформирован",
                        null
                );
                eventService.complete(report.reportId());
            } catch (Throwable exception) {
                fail(exception);
            }
        }

        private void fail(Throwable exception) {
            if (!stopped.compareAndSet(false, true)) {
                return;
            }
            activeSessions.remove(report.reportId(), this);
            failReport(
                    report.reportId(),
                    nextExpectedPosition,
                    jobs.size(),
                    exception
            );
        }

        private void stopForCancellation() {
            stopped.set(true);
            activeSessions.remove(report.reportId(), this);
        }
    }
}
