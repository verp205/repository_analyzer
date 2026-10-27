package com.ivan_naumenko.repository_analyzer.report.orchestrator;

import com.ivan_naumenko.repository_analyzer.commitinsight.dto.CommitInsightResponse;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDraft;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.InsightWorkUnit;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightGenerator;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightPlanner;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightService;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.diff.service.TaskChangeService;
import com.ivan_naumenko.repository_analyzer.report.config.ReportProperties;
import com.ivan_naumenko.repository_analyzer.report.dto.AnalysisReportResponse;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.facts.service.RepositoryFactsService;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
import com.ivan_naumenko.repository_analyzer.report.service.ProjectSynthesisService;
import com.ivan_naumenko.repository_analyzer.report.service.ReportPeriodResolver;
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
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Формирует отчёт в два этапа:
 * 1) анализ коммитов периода без привязки к роли (результат общий для всех
 *    отчётов задачи и переиспользуется);
 * 2) ролевой синтез по фактам и результатам анализа.
 * Остановка отчёта прерывает выполняющиеся потоки, а вместе с ними —
 * HTTP-запросы к модели.
 */
@Component
public class ReportOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(
            ReportOrchestrator.class
    );

    private final AnalysisReportService reportService;
    private final TaskChangeService changeService;
    private final ReportPeriodResolver periodResolver;
    private final CommitInsightService insightService;
    private final CommitInsightPlanner planner;
    private final CommitInsightGenerator generator;
    private final RepositoryFactsService factsService;
    private final ProjectSynthesisService synthesisService;
    private final ReportEventService eventService;
    private final ReportProperties properties;
    private final ExecutorService executor;
    private final Map<Integer, GenerationSession> activeSessions =
            new ConcurrentHashMap<>();

    public ReportOrchestrator(
            AnalysisReportService reportService,
            TaskChangeService changeService,
            ReportPeriodResolver periodResolver,
            CommitInsightService insightService,
            CommitInsightPlanner planner,
            CommitInsightGenerator generator,
            RepositoryFactsService factsService,
            ProjectSynthesisService synthesisService,
            ReportEventService eventService,
            ReportProperties properties,
            @Qualifier("analysisExecutor") ExecutorService executor
    ) {
        this.reportService = reportService;
        this.changeService = changeService;
        this.periodResolver = periodResolver;
        this.insightService = insightService;
        this.planner = planner;
        this.generator = generator;
        this.factsService = factsService;
        this.synthesisService = synthesisService;
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
                ReportStatus.CANCELLED,
                current.processedCommits(),
                current.totalCommits(),
                "Формирование отчёта остановлено пользователем",
                List.of()
        );
        eventService.complete(reportId);
        return true;
    }

    private void prepare(Integer reportId) {
        try {
            ReportGenerationData report = reportService.getGenerationData(
                    reportId
            );
            List<CommitChangeSummary> commits = reportService.loadCommits(reportId);
            Set<Integer> reusable = insightService.findReusableCommitIds(
                    report.taskId()
            );
            List<CommitChangeSummary> pending = commits.stream()
                    .filter(commit -> !reusable.contains(commit.commitId()))
                    .toList();
            List<InsightWorkUnit> units = planner.plan(pending);
            int alreadyAnalyzed = commits.size() - pending.size();

            reportService.initializeProgress(reportId, commits.size());
            if (alreadyAnalyzed > 0) {
                reportService.updateProgress(reportId, alreadyAnalyzed);
            }
            publish(
                    "STARTED",
                    reportId,
                    ReportStatus.PROCESSING,
                    alreadyAnalyzed,
                    commits.size(),
                    pending.isEmpty()
                            ? "Коммиты уже проанализированы, формируются выводы"
                            : "Анализ коммитов начат: " + report.period().label(),
                    List.of()
            );
            log.info(
                    "Отчёт {} ({}): коммитов {}, уже проанализировано {}, единиц работы {}",
                    reportId,
                    report.period().label(),
                    commits.size(),
                    alreadyAnalyzed,
                    units.size()
            );

            GenerationSession session = new GenerationSession(
                    report,
                    commits,
                    units,
                    alreadyAnalyzed
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
                    ReportStatus.FAILED,
                    processed,
                    total,
                    Objects.toString(cause.getMessage(), "Неизвестная ошибка"),
                    List.of()
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
            ReportStatus status,
            int processed,
            int total,
            String message,
            List<CommitInsightResponse> commits
    ) {
        eventService.publish(new ReportEvent(
                type,
                reportId,
                status.name(),
                processed,
                total,
                message,
                commits,
                LocalDateTime.now()
        ));
    }

    private final class GenerationSession {

        private final ReportGenerationData report;
        private final List<CommitChangeSummary> commits;
        private final List<InsightWorkUnit> units;
        private final int totalCommits;
        private final AtomicInteger nextUnitIndex = new AtomicInteger();
        private final AtomicInteger completedUnits = new AtomicInteger();
        private final AtomicInteger processedCommits;
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final AtomicBoolean synthesisStarted = new AtomicBoolean();
        private final Object progressMonitor = new Object();
        private final Set<Future<?>> runningUnits = ConcurrentHashMap.newKeySet();
        private final Object synthesisLock = new Object();
        private Thread synthesisThread;

        private GenerationSession(
                ReportGenerationData report,
                List<CommitChangeSummary> commits,
                List<InsightWorkUnit> units,
                int alreadyAnalyzed
        ) {
            this.report = report;
            this.commits = List.copyOf(commits);
            this.units = List.copyOf(units);
            this.totalCommits = commits.size();
            this.processedCommits = new AtomicInteger(alreadyAnalyzed);
        }

        private void start() {
            if (units.isEmpty()) {
                if (synthesisStarted.compareAndSet(false, true)) {
                    beginSynthesis();
                }
                return;
            }
            int initialTasks = Math.min(properties.maxInFlight(), units.size());
            for (int index = 0; index < initialTasks; index++) {
                submitNext();
            }
        }

        private void submitNext() {
            if (stopped.get()) {
                return;
            }
            int unitIndex = nextUnitIndex.getAndIncrement();
            if (unitIndex >= units.size()) {
                return;
            }

            InsightWorkUnit unit = units.get(unitIndex);
            try {
                Future<?> future = executor.submit(() -> runUnit(unit));
                runningUnits.add(future);
                if (stopped.get()) {
                    future.cancel(true);
                }
            } catch (Throwable exception) {
                fail(exception);
            }
        }

        private void runUnit(InsightWorkUnit unit) {
            List<CommitInsightResponse> insights;
            try {
                insights = process(unit);
            } catch (Throwable exception) {
                fail(exception);
                return;
            }
            try {
                accept(insights);
            } catch (Throwable callbackFailure) {
                fail(callbackFailure);
            }
        }

        private List<CommitInsightResponse> process(InsightWorkUnit unit) {
            Map<Integer, CommitInsightDraft> drafts = generator.generate(
                    unit,
                    report.repositoryUrl()
            );
            List<CommitInsightResponse> saved = new ArrayList<>(unit.commits().size());
            Map<Integer, CommitInsightDraft> toSave = new java.util.LinkedHashMap<>();
            for (CommitChangeSummary commit : unit.commits()) {
                if (stopped.get()) {
                    return List.of();
                }
                CommitInsightDraft draft = drafts.get(commit.commitId());
                if (draft == null) {
                    throw new IllegalStateException(
                            "Нет результата анализа коммита " + commit.commitHash()
                    );
                }
                saved.add(insightService.toResponse(commit, draft));
                toSave.put(commit.commitId(), draft);
            }
            if (stopped.get()) return List.of();
            insightService.saveAll(toSave);
            return saved;
        }

        private void accept(List<CommitInsightResponse> insights) {
            boolean shouldSynthesize;
            synchronized (progressMonitor) {
                if (stopped.get()) {
                    return;
                }
                int processed = processedCommits.addAndGet(insights.size());
                int completed = completedUnits.incrementAndGet();
                reportService.updateProgress(report.reportId(), processed);
                publish(
                        "PROGRESS",
                        report.reportId(),
                        ReportStatus.PROCESSING,
                        processed,
                        totalCommits,
                        "Проанализировано коммитов: %d из %d".formatted(
                                processed,
                                totalCommits
                        ),
                        insights
                );
                shouldSynthesize = completed == units.size()
                        && synthesisStarted.compareAndSet(false, true);
            }

            submitNext();
            if (shouldSynthesize) {
                beginSynthesis();
            }
        }

        private void beginSynthesis() {
            synchronized (synthesisLock) {
                if (stopped.get()) {
                    return;
                }
                synthesisThread = Thread.currentThread();
            }

            try {
                publish(
                        "SYNTHESIS",
                        report.reportId(),
                        ReportStatus.PROCESSING,
                        totalCommits,
                        totalCommits,
                        "Коммиты обработаны, формируются выводы для роли «%s»"
                                .formatted(report.audience().displayName()),
                        List.of()
                );
                RepositoryFactsResponse facts = factsService.build(report.taskId(), commits);
                List<CommitInsightResponse> insights = insightService.findForCommits(
                        report.taskId(),
                        commits
                );
                var result = synthesisService.synthesize(
                        report,
                        facts,
                        insights
                );
                if (stopped.get()) {
                    return;
                }
                reportService.complete(report.reportId(), result, facts);
                stopped.set(true);
                activeSessions.remove(report.reportId(), this);
                publish(
                        "COMPLETED",
                        report.reportId(),
                        ReportStatus.COMPLETED,
                        totalCommits,
                        totalCommits,
                        "Отчёт сформирован",
                        List.of()
                );
                eventService.complete(report.reportId());
            } catch (Throwable exception) {
                fail(exception);
            } finally {
                synchronized (synthesisLock) {
                    synthesisThread = null;
                }
            }
        }

        private void fail(Throwable exception) {
            if (!stopped.compareAndSet(false, true)) {
                return;
            }
            activeSessions.remove(report.reportId(), this);
            failReport(
                    report.reportId(),
                    processedCommits.get(),
                    totalCommits,
                    exception
            );
        }

        private void stopForCancellation() {
            stopped.set(true);
            activeSessions.remove(report.reportId(), this);
            runningUnits.forEach(future -> future.cancel(true));
            synchronized (synthesisLock) {
                if (synthesisThread != null) {
                    synthesisThread.interrupt();
                }
            }
        }
    }
}
