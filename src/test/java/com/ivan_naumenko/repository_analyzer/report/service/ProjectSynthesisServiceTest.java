package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.commitinsight.dto.CommitInsightResponse;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.ChangeCategory;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDetails;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightStatus;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.RiskLevel;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.llm.coordinator.LlmRequestCoordinator;
import com.ivan_naumenko.repository_analyzer.llm.prompt.service.ProjectSynthesisPromptFactory;
import com.ivan_naumenko.repository_analyzer.report.config.ReportProperties;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import com.ivan_naumenko.repository_analyzer.report.snapshot.RepositorySnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private AnalysisReportService reportService;

    @Mock
    private com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifactService artifactService;

    @Test
    void summarizesLargeHistoryInBatchesBeforeFinalSynthesis() {
        ReportGenerationData report = report(ReportAudience.ANALYST);
        List<CommitInsightResponse> insights = List.of(
                insight(10, "x".repeat(2_500), RiskLevel.LOW, ChangeCategory.FEATURE),
                insight(20, "y".repeat(2_500), RiskLevel.LOW, ChangeCategory.FEATURE),
                insight(30, "z".repeat(2_500), RiskLevel.LOW, ChangeCategory.FEATURE)
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
        when(promptFactory.buildFinal(eq(report), any(), any(), anyList(), any()))
                .thenReturn("final-prompt");
        when(requestCoordinator.getOrGenerate(
                "final-prompt",
                ReportAudience.ANALYST,
                LlmRequestProfile.SYNTHESIS_FINAL
        )).thenReturn("""
                <think>рассуждения</think>
                # Отчёт
                ## Главное

                | Вывод | Основание |
                |---|---|
                | Проект развивается | История |
                """);

        when(artifactService.parse(any(), eq(report), any(), any())).thenReturn(
                new com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.SynthesisResult("## Главное", null));
        String synthesis = service().synthesize(report, null, insights).content();

        verify(promptFactory, times(3)).buildIntermediate(
                eq(report),
                eq(1),
                anyInt(),
                eq(3),
                anyList()
        );
        assertTrue(synthesis.startsWith("## Главное"));

        assertFalse(synthesis.contains("|---|"));
        assertFalse(synthesis.contains("рассуждения"));
        assertFalse(synthesis.contains("# Отчёт"));
    }

    @Test
    void selectsRoleSpecificDetailsAndSkipsMergesAndFailures() {
        ReportGenerationData report = report(ReportAudience.DIRECTOR);
        List<CommitInsightResponse> insights = List.of(
                insight(10, "Добавлена оплата", RiskLevel.HIGH, ChangeCategory.FEATURE),
                insight(20, "Слияние веток", RiskLevel.NONE, ChangeCategory.MERGE),
                new CommitInsightResponse(
                        30, 2, "%040d".formatted(30), "m", "a", Instant.EPOCH,
                        1, 1, 1, CommitInsightStatus.FAILED, ChangeCategory.CHORE,
                        "Сопровождение", RiskLevel.UNKNOWN, "Ошибка", CommitInsightDetails.empty()
                )
        );
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> sources = ArgumentCaptor.forClass(List.class);
        when(promptFactory.buildFinal(eq(report), any(), any(), sources.capture(), any()))
                .thenReturn("final-prompt");
        when(requestCoordinator.getOrGenerate(
                "final-prompt",
                ReportAudience.DIRECTOR,
                LlmRequestProfile.SYNTHESIS_FINAL
        )).thenReturn("## Главное");

        service().synthesize(report, null, insights);

        assertEquals(1, sources.getValue().size());
        String source = sources.getValue().getFirst();
        assertTrue(source.startsWith("C10 [0000000] "));
        assertTrue(source.contains("Эффект: Пользователи могут оплатить заказ"));
        assertTrue(source.contains("Риски: Нет проверки суммы"));
        assertFalse(source.contains("Тесты:"));
    }

    @Test
    void discardsInvalidCachedSynthesisSoRetryCanGenerateAgain() {
        ReportGenerationData report = report(ReportAudience.DEVELOPER);
        when(promptFactory.buildFinal(eq(report), any(), any(), anyList(), any())).thenReturn("final-prompt");
        when(requestCoordinator.getOrGenerate(startsWith("final-prompt"), eq(report.audience()), eq(LlmRequestProfile.SYNTHESIS_FINAL)))
                .thenReturn("invalid");
        when(artifactService.parse(eq("invalid"), eq(report), any(), any())).thenThrow(new IllegalStateException("Bad JSON"));
        assertThrows(IllegalStateException.class, () -> service().synthesize(report, null, List.of()));
        verify(requestCoordinator).discardResponse("final-prompt", LlmRequestProfile.SYNTHESIS_FINAL, "invalid");
        verify(requestCoordinator).discardResponse(startsWith("final-prompt\n"), eq(LlmRequestProfile.SYNTHESIS_FINAL), eq("invalid"));
    }

    private com.ivan_naumenko.repository_analyzer.report.grounding.GroundedReviewService passthroughReview() {
        var review = mock(com.ivan_naumenko.repository_analyzer.report.grounding.GroundedReviewService.class);
        lenient().when(review.refine(any(), any(), anyList(), anyList(), any())).thenAnswer(call -> call.getArgument(4));
        return review;
    }

    private ProjectSynthesisService service() {
        return new ProjectSynthesisService(
                promptFactory,
                requestCoordinator,
                new ReportTextSanitizer(),
                new ReportProperties(3, 4_000, 3, 5, 40),
                reportService, artifactService, passthroughReview()
        );
    }

    private ReportGenerationData report(ReportAudience audience) {
        return new ReportGenerationData(
                8,
                4,
                audience,
                "https://example.com/large.git",
                "main"
        );
    }

    private CommitInsightResponse insight(
            int commitId,
            String summary,
            RiskLevel risk,
            ChangeCategory category
    ) {
        return new CommitInsightResponse(
                commitId,
                commitId / 10 - 1,
                "%040d".formatted(commitId),
                "Commit " + commitId,
                "Test User",
                Instant.parse("2026-07-20T10:00:00Z"),
                10,
                2,
                1,
                CommitInsightStatus.ANALYZED,
                category,
                category.displayName(),
                risk,
                summary,
                new CommitInsightDetails(
                        List.of("PaymentService — новый метод"),
                        "Пользователи могут оплатить заказ",
                        List.of("Нет проверки суммы"),
                        List.of(),
                        "Тесты отсутствуют",
                        List.of()
                )
        );
    }
}
