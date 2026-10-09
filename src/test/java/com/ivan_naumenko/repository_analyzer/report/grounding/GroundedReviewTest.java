package com.ivan_naumenko.repository_analyzer.report.grounding;

import com.ivan_naumenko.repository_analyzer.diff.model.*;
import com.ivan_naumenko.repository_analyzer.evaluation.FinalReportFixture;
import com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmCancelledException;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.llm.coordinator.LlmRequestCoordinator;
import com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts;
import com.ivan_naumenko.repository_analyzer.report.config.ReportProperties;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.*;

class GroundedReviewTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GroundedReviewParser parser = new GroundedReviewParser(mapper);
    private final ReviewContext context = new ReviewContext(101, FinalReportFixture.hash(), List.of(
            new ReviewContext.SourceFile("auth.ts", "auth.ts", "MODIFY", 1, 1, "-checkSession();\n+next();")),
            List.of(new Citation("D0", "C101", "auth.ts", FinalReportFixture.hash(), "DIFF", 1, 2, "-checkSession();\n+next();")),
            1, 0, 0, List.of("Цепочка вызовов не проверена."));

    private String response() {
        return """
                {"findings":[{"title":"Удалён вызов checkSession", "impact":"Если это единственная проверка, запрос без сессии достигнет обработчика.",
                "riskLevel":"HIGH", "riskMechanism":"ACCESS", "risk":"Возможен обход проверки сессии.",
                "action":"Проверить запрос без сессии", "verification":"Запрос без cookie должен получить 401, обработчик не вызывается.",
                "sourceIds":["D0"], "mitigationIds":[], "unknowns":["Неизвестны проверки во внешнем middleware."]}],"unknowns":[]}
                """;
    }
    @Test void returnsServerOwnedQuoteAndConditionalAssessment() {
        var review = parser.parse(response(), context, "LOW");
        assertSame(context.citations().getFirst(), review.items().getFirst().citations().getFirst());
        assertEquals("HIGH", review.items().getFirst().riskLevel());
        assertFalse(review.items().getFirst().unknowns().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"missing-source", "underestimated-risk", "missing-action", "fake-test-success", "invented-protection", "empty-after-cleaning"})
    void rejectsUnsupportedOrInconsistentClaims(String flaw) {
        String invalid = switch (flaw) {
            case "missing-source" -> response().replace("\"D0\"", "\"D99\"");
            case "underestimated-risk" -> response().replace("\"HIGH\"", "\"LOW\"");
            case "missing-action" -> response().replace("Проверить запрос без сессии", "");
            case "fake-test-success" -> response().replace("Удалён вызов checkSession", "Тесты прошли");
            case "invented-protection" -> response().replace("\"mitigationIds\":[]", "\"mitigationIds\":[\"D99\"]");
            default -> response().replace("Удалён вызов checkSession", "<span></span>");
        };
        assertThrows(IllegalStateException.class, () -> parser.parse(invalid, context, "LOW"));
    }
    @Test void absenceOfFindingsRequiresAnExplicitUnknown() {
        assertThrows(IllegalStateException.class, () -> parser.parse("{\"findings\":[],\"unknowns\":[]}", context, "LOW"));
        assertTrue(parser.parse("{\"findings\":[],\"unknowns\":[\"Не хватает вызывающего кода.\"]}", context, "LOW").items().isEmpty());
    }
    @Test void repairsInvalidResponseAndReplacesUnverifiedDraftAndRiskCounters() {
        var contexts = mock(ReviewContextService.class);
        var requests = mock(LlmRequestCoordinator.class);
        when(contexts.load(any(), eq(101))).thenReturn(context);
        when(requests.getOrGenerate(anyString(), isNull(), any()))
                .thenReturn(response().replace("\"D0\"", "\"D99\""), response(), "{\"supported\":true,\"issues\":[]}");
        var result = service(contexts, requests).refine(FinalReportFixture.report(ReportAudience.DEVELOPER),
                FinalReportFixture.facts(), List.of(commit()), List.of(), draft());
        verify(requests).discardResponse(anyString(), eq(LlmRequestProfile.GROUNDED_REVIEW), contains("D99"));
        assertEquals(1, result.artifacts().coverage().rejectedResponses());
        assertEquals(1, result.artifacts().coverage().reviewedCommits());
        assertFalse(result.content().contains("Выдуманное утверждение"));
        assertEquals("INFERENCE", result.artifacts().sections().getFirst().findings().getFirst().assessment().kind());
        assertEquals(1, ReviewedFacts.apply(FinalReportFixture.facts(), result.artifacts().coverage()).risks().high());
        assertEquals(1, mapper.readTree(result.artifacts().evidence().getFirst().description()).path("risks").path("high").intValue());
        assertFalse(result.artifacts().actions().isEmpty());
    }
    @Test void repeatedFailureLeavesExplicitUnknownAndNeverPublishesTheOldClaim() {
        var contexts = mock(ReviewContextService.class); var requests = mock(LlmRequestCoordinator.class);
        when(contexts.load(any(), eq(101))).thenReturn(context);
        when(requests.getOrGenerate(anyString(), isNull(), any())).thenReturn("not-json");
        var result = service(contexts, requests).refine(FinalReportFixture.report(ReportAudience.DEVELOPER),
                FinalReportFixture.facts(), List.of(commit()), List.of(), draft());
        assertEquals(1, result.artifacts().coverage().unreviewedCommits());
        assertEquals("UNKNOWN", result.artifacts().coverage().reviewedRisks().get(101));
        assertEquals("PARTIAL", result.artifacts().quality().status());
        assertFalse(result.content().contains("Выдуманное утверждение"));
        assertTrue(result.artifacts().actions().isEmpty());
        verify(requests, times(2)).discardResponse(anyString(), any(), eq("not-json"));
    }
    @Test void cancellationDoesNotBecomeAnUnknownOrTriggerRetry() {
        var contexts = mock(ReviewContextService.class); var requests = mock(LlmRequestCoordinator.class);
        when(contexts.load(any(), eq(101))).thenReturn(context);
        when(requests.getOrGenerate(anyString(), isNull(), any())).thenThrow(new LlmCancelledException("stop"));
        assertThrows(LlmCancelledException.class, () -> service(contexts, requests).refine(FinalReportFixture.report(ReportAudience.DEVELOPER),
                FinalReportFixture.facts(), List.of(commit()), List.of(), draft()));
        verify(requests, times(1)).getOrGenerate(anyString(), isNull(), any());
    }
    @Test void semanticRejectionRepairsTheCandidateWithoutPublishingTheFalseClaim() {
        var contexts = mock(ReviewContextService.class); var requests = mock(LlmRequestCoordinator.class);
        when(contexts.load(any(), eq(101))).thenReturn(context);
        String unsupported = response().replace("Удалён вызов checkSession", "Проверка сессии усилена");
        when(requests.getOrGenerate(anyString(), isNull(), any()))
                .thenReturn(unsupported, "{\"supported\":false,\"issues\":[\"Проверка удалена, а не усилена.\"]}",
                        response(), "{\"supported\":true,\"issues\":[]}");
        var result = service(contexts, requests).refine(FinalReportFixture.report(ReportAudience.DEVELOPER),
                FinalReportFixture.facts(), List.of(commit()), List.of(), draft());
        assertFalse(result.content().contains("усилена"));
        assertEquals(1, result.artifacts().coverage().reviewedCommits());
        assertEquals(1, result.artifacts().coverage().rejectedResponses());
        verify(requests).discardResponse(anyString(), any(), eq(unsupported));
        verify(requests, times(2)).getOrGenerate(anyString(), isNull(), eq(LlmRequestProfile.GROUNDED_REVIEW));
        verify(requests, times(2)).getOrGenerate(anyString(), isNull(), eq(LlmRequestProfile.GROUNDED_VERIFY));
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"supported\":\"true\",\"issues\":[]}",
            "{\"supported\":true,\"issues\":[\"Contradiction\"]}", "{\"supported\":false,\"issues\":[]}"})
    void rejectsMissingOrContradictorySemanticVerdicts(String raw) {
        assertThrows(IllegalStateException.class, () -> parser.verdict(raw));
    }
    @Test void sourceProjectionKeepsDeletedCodeOutOfTheNewVersion() {
        String patch = "diff --git a/a.py b/a.py\n--- a/a.py\n+++ b/a.py\n@@ -1,2 +1,2 @@\n-if amount <= 0: reject()\n+if amount == 0: reject()\n debit(amount)\n";
        assertEquals("\nif amount <= 0: reject()\ndebit(amount)\n", GroundedReviewPrompt.fragment(patch, false));
        assertEquals("\nif amount == 0: reject()\ndebit(amount)\n", GroundedReviewPrompt.fragment(patch, true));
    }
    @Test void safeDefaultPreservesRoleAnalysisAndRiskCountsWithoutExtraModelCalls() {
        var contexts = mock(ReviewContextService.class); var requests = mock(LlmRequestCoordinator.class);
        when(contexts.load(any(), eq(101))).thenReturn(context);
        var finding = new Finding("Удалён вызов checkSession", List.of("C101"));
        var draft = new SynthesisResult("", new ReportArtifacts(List.of(finding), List.of(new Section("Риски", List.of(finding))),
                List.of(), FinalReportFixture.evidence(), new Quality("CHECKED", 1, 0, List.of("Структура роли проверена"), List.of("Поведение внешнего middleware неизвестно."))));
        var service = new GroundedReviewService(contexts, new GroundedReviewPrompt(mapper), parser, requests,
                new ReportProperties(2, 4000, 3, 5, 40), mapper);
        var result = service.refine(FinalReportFixture.report(ReportAudience.DEVELOPER), FinalReportFixture.facts(), List.of(commit()), List.of(), draft);
        assertEquals("SOURCES_ONLY", result.artifacts().coverage().mode());
        assertEquals("FACT", result.artifacts().summary().getFirst().assessment().kind());
        var preserved = result.artifacts().sections().getFirst().findings().getFirst();
        assertEquals(finding.text(), preserved.text());
        assertEquals("INFERENCE", preserved.assessment().kind());
        assertEquals(context.citations(), preserved.assessment().citations());
        assertTrue(result.artifacts().coverage().limitations().contains("Поведение внешнего middleware неизвестно."));
        assertTrue(result.content().contains("Поведение внешнего middleware неизвестно."));
        assertTrue(result.artifacts().quality().checks().contains("Структура роли проверена"));
        assertEquals(FinalReportFixture.facts(), ReviewedFacts.apply(FinalReportFixture.facts(), result.artifacts().coverage()));
        verifyNoInteractions(requests);
    }
    private GroundedReviewService service(ReviewContextService contexts, LlmRequestCoordinator requests) {
        return new GroundedReviewService(contexts, new GroundedReviewPrompt(mapper), parser, requests,
                new ReportProperties(2, 4000, 3, 5, 40, true), mapper);
    }
    private CommitChangeSummary commit() {
        return new CommitChangeSummary(0, 101, FinalReportFixture.hash(), "Change auth", "Author", null, 1, 1,
                List.of(new ChangedFileRow(101, "auth.ts", "auth.ts", FileChangeType.MODIFY, 1, 1)), 1);
    }
    private SynthesisResult draft() {
        return new SynthesisResult("Выдуманное утверждение", new ReportArtifacts(List.of(),
                List.of(new Section("Риски", List.of(new Finding("Выдуманное утверждение", List.of("C101"))))),
                List.of(), FinalReportFixture.evidence(), new Quality("CHECKED", 1, 0, List.of(), List.of())));
    }
}
