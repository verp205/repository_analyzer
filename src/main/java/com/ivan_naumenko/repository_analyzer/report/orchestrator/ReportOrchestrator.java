package com.ivan_naumenko.repository_analyzer.report.orchestrator;

import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.llm.context.model.CompactCommitContext;
import com.ivan_naumenko.repository_analyzer.llm.context.service.StoredCommitContextService;
import com.ivan_naumenko.repository_analyzer.llm.coordinator.LlmRequestCoordinator;
import com.ivan_naumenko.repository_analyzer.llm.prompt.service.PromptFactory;
import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.CommitClustersResponse;
import com.ivan_naumenko.repository_analyzer.insight.cluster.service.CommitClusteringService;
import com.ivan_naumenko.repository_analyzer.insight.graph.dto.CommitGraphResponse;
import com.ivan_naumenko.repository_analyzer.insight.graph.service.CommitGraphService;
import com.ivan_naumenko.repository_analyzer.report.model.CommitReportResult;
import com.ivan_naumenko.repository_analyzer.report.model.ReportCommitJob;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
import com.ivan_naumenko.repository_analyzer.report.sse.ReportEvent;
import com.ivan_naumenko.repository_analyzer.report.sse.ReportEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

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
    private final ReportEventService eventService;
    private final ExecutorService executor;

    public ReportOrchestrator(
            AnalysisReportService reportService,
            RepositoryCommitRepository commitRepository,
            StoredCommitContextService contextService,
            PromptFactory promptFactory,
            LlmRequestCoordinator requestCoordinator,
            CommitGraphService graphService,
            CommitClusteringService clusteringService,
            ReportEventService eventService,
            @Qualifier("analysisExecutor") ExecutorService executor
    ) {
        this.reportService = reportService;
        this.commitRepository = commitRepository;
        this.contextService = contextService;
        this.promptFactory = promptFactory;
        this.requestCoordinator = requestCoordinator;
        this.graphService = graphService;
        this.clusteringService = clusteringService;
        this.eventService = eventService;
        this.executor = executor;
    }

    public boolean start(Integer reportId) {
        Objects.requireNonNull(reportId, "reportId не должен быть null");
        if (!reportService.claim(reportId)) {
            return false;
        }

        try {
            executor.execute(() -> generate(reportId));
            return true;
        } catch (RejectedExecutionException exception) {
            reportService.returnToNew(reportId);
            throw exception;
        }
    }

    private void generate(Integer reportId) {
        try {

            ReportGenerationData report = reportService.getGenerationData(
                    reportId
            );

            List<Integer> commitIds = commitRepository.findIdsForReport(
                    report.taskId(),
                    CommitProcessingStatus.COMPLETED
            );

            List<ReportCommitJob> commitsWithPosition = new ArrayList<>();

            for (int position = 0; position < commitIds.size(); position++) {
                commitsWithPosition.add(new ReportCommitJob(position, commitIds.get(position)));
            }

            reportService.initializeProgress(reportId, commitIds.size());
            CommitGraphResponse graph = graphService.build(report.taskId());
            CommitClustersResponse clusters = clusteringService.cluster(
                    report.taskId()
            );
            publish(
                    "STARTED",
                    reportId,
                    0,
                    commitIds.size(),
                    "Формирование отчёта начато",
                    null
            );

            StringBuilder content = new StringBuilder()
                    .append("# Отчёт по истории репозитория\n\n")
                    .append("Аудитория: ")
                    .append(report.audience())
                    .append("\n\n")
                    .append("## Техническая сводка\n\n")
                    .append("- Коммитов: ")
                    .append(graph.totalCommits())
                    .append("\n- Корней графа: ")
                    .append(graph.rootCount())
                    .append("\n- Ветвей без потомков: ")
                    .append(graph.tipCount())
                    .append("\n- Merge-коммитов: ")
                    .append(graph.mergeCommitCount())
                    .append("\n- Максимальная глубина: ")
                    .append(graph.maxDepth())
                    .append("\n\n## Группы изменений\n\n");

            if (clusters.clusters().isEmpty()) {
                content.append("Изменения для кластеризации отсутствуют.\n\n");
            } else {
                clusters.clusters().forEach(cluster -> content
                        .append("- ")
                        .append(cluster.displayName())
                        .append(": ")
                        .append(cluster.commits().size())
                        .append("\n"));
                content.append("\n");
            }

            reportService.updateProgress(reportId, 0, content.toString());
            publish(
                    "SUMMARY",
                    reportId,
                    0,
                    commitIds.size(),
                    "Техническая сводка сформирована",
                    content.toString()
            );

            for (ReportCommitJob job : commitsWithPosition) {
                CommitReportResult result = processCommit(report, job);

                content.append(result.content());

                int processed = result.position() + 1;

                reportService.updateProgress(
                        reportId,
                        processed,
                        content.toString()
                );

                publish(
                        "PROGRESS",
                        reportId,
                        processed,
                        commitIds.size(),
                        "Обработан коммит с ID " + result.commitId(),
                        result.content()
                );
            }

            reportService.complete(reportId, content.toString());
            publish(
                    "COMPLETED",
                    reportId,
                    commitIds.size(),
                    commitIds.size(),
                    "Отчёт сформирован",
                    null
            );
            eventService.complete(reportId);
        } catch (Exception exception) {
            reportService.fail(reportId, exception);
            publish(
                    "FAILED",
                    reportId,
                    0,
                    0,
                    exception.getMessage(),
                    null
            );
            eventService.complete(reportId);
            log.error("Формирование отчёта {} завершилось ошибкой", reportId, exception);
        }
    }

    private CommitReportResult processCommit(ReportGenerationData report, ReportCommitJob job) {

        CompactCommitContext context = contextService.load(job.commitId());

        String prompt = promptFactory.build(context, report.audience());

        String response = requestCoordinator.getOrGenerate(prompt, report.audience());

        String commitSection = """
        ## %s — %s

        %s

        """.formatted(context.commitHash(), context.message(), response);

        return new CommitReportResult(
                job.position(),
                job.commitId(),
                commitSection
        );
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
}
