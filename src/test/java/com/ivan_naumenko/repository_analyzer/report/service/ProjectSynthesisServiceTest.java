package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.CommitClustersResponse;
import com.ivan_naumenko.repository_analyzer.insight.graph.dto.CommitGraphResponse;
import com.ivan_naumenko.repository_analyzer.llm.coordinator.LlmRequestCoordinator;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.llm.prompt.service.ProjectSynthesisPromptFactory;
import com.ivan_naumenko.repository_analyzer.report.config.ReportProperties;
import com.ivan_naumenko.repository_analyzer.report.model.CommitReportResult;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectSynthesisServiceTest {

    @Mock
    private ProjectSynthesisPromptFactory promptFactory;

    @Mock
    private LlmRequestCoordinator requestCoordinator;

    @Test
    void summarizesLargeHistoryInBatchesBeforeFinalSynthesis() {
        ReportGenerationData report = new ReportGenerationData(
                8,
                4,
                ReportAudience.ANALYST,
                "https://example.com/large.git",
                "main"
        );
        CommitGraphResponse graph = new CommitGraphResponse(
                4,
                3,
                1,
                1,
                0,
                3,
                0,
                List.of(),
                List.of()
        );
        CommitClustersResponse clusters = new CommitClustersResponse(
                4,
                0,
                List.of()
        );
        List<CommitReportResult> results = List.of(
                result(0, 10),
                result(1, 20),
                result(2, 30)
        );

        when(promptFactory.buildIntermediate(
                eq(report),
                anyInt(),
                anyInt(),
                anyInt(),
                anyList()
        )).thenAnswer(invocation -> "batch-" + invocation.getArgument(2));
        when(requestCoordinator.getOrGenerate(
                startsWith("batch-"),
                eq(ReportAudience.ANALYST),
                eq(LlmRequestProfile.SYNTHESIS_BATCH)
        )).thenReturn("Краткая сводка группы.");
        when(promptFactory.buildFinal(
                eq(report),
                eq(graph),
                eq(clusters),
                anyList()
        )).thenReturn("final-prompt");
        when(requestCoordinator.getOrGenerate(
                "final-prompt",
                ReportAudience.ANALYST,
                LlmRequestProfile.SYNTHESIS_FINAL
        )).thenReturn("""
                ## 1. Общая картина проекта

                | Вывод | Основание |
                |---|---|
                | Проект развивается | История |
                """);

        ProjectSynthesisService service = new ProjectSynthesisService(
                promptFactory,
                requestCoordinator,
                new ReportTextSanitizer(),
                new ReportProperties(3, 4_000, 3)
        );

        String synthesis = service.synthesize(
                report,
                graph,
                clusters,
                results
        );

        verify(promptFactory, times(3)).buildIntermediate(
                eq(report),
                eq(1),
                anyInt(),
                eq(3),
                anyList()
        );
        verify(promptFactory).buildFinal(
                eq(report),
                eq(graph),
                eq(clusters),
                anyList()
        );
        assertTrue(synthesis.contains("- Проект развивается; История"));
        assertFalse(synthesis.contains("|---|"));
    }

    private CommitReportResult result(int position, int commitId) {
        return new CommitReportResult(
                position,
                commitId,
                "%040d".formatted(commitId),
                "Commit " + commitId,
                "x".repeat(2_500)
        );
    }
}
