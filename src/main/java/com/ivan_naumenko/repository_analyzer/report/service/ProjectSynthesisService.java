package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifactService;
import com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.SynthesisResult;
import com.ivan_naumenko.repository_analyzer.commitinsight.dto.CommitInsightResponse;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.ChangeCategory;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDetails;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightStatus;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.PullRequestRef;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.RiskLevel;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.llm.coordinator.LlmRequestCoordinator;
import com.ivan_naumenko.repository_analyzer.llm.prompt.service.ProjectSynthesisPromptFactory;
import com.ivan_naumenko.repository_analyzer.report.config.ReportProperties;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
@RequiredArgsConstructor
public class ProjectSynthesisService {

    private static final String EMPTY_SOURCE =
            "Коммиты с результатами смыслового анализа отсутствуют.";

    private final ProjectSynthesisPromptFactory promptFactory;
    private final LlmRequestCoordinator requestCoordinator;
    private final ReportTextSanitizer sanitizer;
    private final ReportProperties properties;
    private final AnalysisReportService reportService;
    private final ReportArtifactService artifactService;
    private final com.ivan_naumenko.repository_analyzer.report.grounding.GroundedReviewService groundedReview;
    // Промежуточные сводки ждут LLM; число реальных запросов ограничивает LlmClient.
    private final ExecutorService batchExecutor =
            Executors.newVirtualThreadPerTaskExecutor();

    public SynthesisResult synthesize(
            ReportGenerationData report,
            RepositoryFactsResponse facts,
            List<CommitInsightResponse> insights
    ) {
        List<String> sources = insights.stream()
                .filter(this::isUsefulSource)
                .map(insight -> toSource(insight, report.audience()))
                .toList();
        if (sources.isEmpty()) {
            sources = List.of(EMPTY_SOURCE);
        }

        int pass = 1;
        while (totalCharacters(sources) > properties.synthesisBatchChars()
                && pass <= properties.synthesisMaxPasses()) {
            sources = summarizeBatches(
                    report,
                    pass,
                    splitIntoBatches(sources, properties.synthesisBatchChars())
            );
            pass++;
        }

        var snapshot = reportService.snapshot(report.reportId());
        var commits = reportService.loadCommits(report.reportId());
        var evidence = artifactService.catalog(report, facts, snapshot, commits, insights);
        String finalPrompt = promptFactory.buildFinal(
                report,
                facts,
                snapshot,
                sources, artifactService.promptCatalog(evidence, sources)
        );
        var draft = ValidatedSynthesis.generate(finalPrompt, report, facts, evidence, artifactService,
                prompt -> requestCoordinator.getOrGenerate(prompt, report.audience(), LlmRequestProfile.SYNTHESIS_FINAL),
                (prompt, raw) -> requestCoordinator.discardResponse(prompt, LlmRequestProfile.SYNTHESIS_FINAL, raw));
        return groundedReview.refine(report, facts, commits, insights, draft);
    }

    @PreDestroy
    void shutdown() {
        batchExecutor.shutdownNow();
    }

    private List<String> summarizeBatches(
            ReportGenerationData report,
            int pass,
            List<List<String>> batches
    ) {
        List<Future<String>> futures = new ArrayList<>(batches.size());
        try {
            for (int index = 0; index < batches.size(); index++) {
                int batchIndex = index;
                futures.add(batchExecutor.submit(
                        () -> sanitizer.sanitize(requestCoordinator.getOrGenerate(
                                promptFactory.buildIntermediate(
                                        report,
                                        pass,
                                        batchIndex,
                                        batches.size(),
                                        batches.get(batchIndex)
                                ),
                                report.audience(),
                                LlmRequestProfile.SYNTHESIS_BATCH
                        ))
                ));
            }
            List<String> summaries = new ArrayList<>(futures.size());
            for (Future<String> future : futures) {
                summaries.add(future.get());
            }
            return summaries;
        } catch (InterruptedException exception) {
            futures.forEach(future -> future.cancel(true));
            Thread.currentThread().interrupt();
            throw new CancellationException("Формирование сводки остановлено");
        } catch (ExecutionException exception) {
            futures.forEach(future -> future.cancel(true));
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException(
                    "Промежуточная сводка не сформирована",
                    exception.getCause()
            );
        }
    }

    private boolean isUsefulSource(CommitInsightResponse insight) {
        return insight.status() != CommitInsightStatus.FAILED
                && insight.category() != ChangeCategory.MERGE;
    }

    /**
     * Компактная строка для синтеза. Детали подбираются под роль, чтобы
     * модель видела то, что важно читателю, и не тратила контекст на остальное.
     */
    private String toSource(CommitInsightResponse insight, ReportAudience audience) {
        StringBuilder source = new StringBuilder()
                .append("C").append(insight.commitId()).append(" [")
                .append(shortHash(insight.commitHash()))
                .append("] ")
                .append(insight.committedAt() == null
                        ? "дата неизвестна"
                        : insight.committedAt().atZone(ZoneOffset.UTC).toLocalDate())
                .append(" · ")
                .append(insight.categoryName())
                .append(" · риск: ")
                .append(insight.riskLevel().displayName())
                .append('\n')
                .append(insight.summary());

        CommitInsightDetails details = insight.details();
        PullRequestRef pullRequest = details.pullRequest();
        if (pullRequest != null) {
            source.append("\nPR #").append(pullRequest.number()).append(": ").append(pullRequest.title());
            if (audience != ReportAudience.DEVELOPER && !pullRequest.issueTitles().isEmpty()) {
                source.append("\nЗадачи: ").append(String.join("; ", pullRequest.issueTitles()));
            }
        }

        boolean significant = insight.riskLevel().isAtLeast(RiskLevel.MEDIUM);
        switch (audience) {
            case DEVELOPER -> {
                append(source, "Изменения", details.keyChanges());
                append(source, "Риски", details.risks());
                append(source, "Тесты", details.testNotes());
            }
            case ANALYST -> {
                append(source, "Поведение", details.behaviorImpact());
                append(source, "Изменения", details.keyChanges());
            }
            case SECURITY -> {
                append(source, "Безопасность", details.securityNotes());
                if (significant || !details.securityNotes().isEmpty()) {
                    append(source, "Риски", details.risks());
                    append(source, "Изменения", details.keyChanges());
                }
            }
            case TEAM_LEAD -> {
                append(source, "Риски", details.risks());
                append(source, "Тесты", details.testNotes());
                append(source, "Проверить", details.checks());
            }
            case DIRECTOR -> {
                append(source, "Эффект", details.behaviorImpact());
                if (significant) {
                    append(source, "Риски", details.risks());
                }
            }
        }
        return source.toString();
    }

    private void append(StringBuilder source, String label, List<String> values) {
        if (!values.isEmpty()) {
            source.append('\n').append(label).append(": ").append(String.join("; ", values));
        }
    }

    private void append(StringBuilder source, String label, String value) {
        if (value != null && !value.isBlank()) {
            source.append('\n').append(label).append(": ").append(value);
        }
    }

    private String shortHash(String hash) {
        return hash == null || hash.length() <= 7 ? String.valueOf(hash) : hash.substring(0, 7);
    }

    private List<List<String>> splitIntoBatches(
            List<String> sources,
            int maxCharacters
    ) {
        List<List<String>> batches = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentCharacters = 0;

        for (String source : sources) {
            if (!current.isEmpty()
                    && currentCharacters + source.length() > maxCharacters) {
                batches.add(List.copyOf(current));
                current.clear();
                currentCharacters = 0;
            }
            current.add(source);
            currentCharacters += source.length();
        }

        if (!current.isEmpty()) {
            batches.add(List.copyOf(current));
        }
        return batches;
    }

    private int totalCharacters(List<String> sources) {
        return sources.stream().mapToInt(String::length).sum();
    }
}
