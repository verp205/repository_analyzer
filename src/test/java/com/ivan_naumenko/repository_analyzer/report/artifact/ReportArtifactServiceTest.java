package com.ivan_naumenko.repository_analyzer.report.artifact;

import com.ivan_naumenko.repository_analyzer.evaluation.FinalReportFixture;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

class ReportArtifactServiceTest {
    private final ReportArtifactService service = new ReportArtifactService(new ObjectMapper());
    private final ReportAudience role = ReportAudience.DEVELOPER;

    @ParameterizedTest
    @EnumSource(ReportAudience.class)
    void preservesGroundedFindingsAndActionAcceptanceCriteriaForEveryRole(ReportAudience audience) {
        var result = service.parse(FinalReportFixture.response(audience), FinalReportFixture.report(audience),
                FinalReportFixture.facts(), FinalReportFixture.evidence());
        assertTrue(result.content().contains("[C101](#evidence-C101)"));
        assertEquals(FinalReportFixture.path(), result.artifacts().actions().getFirst().paths().getFirst());
        assertTrue(result.artifacts().actions().getFirst().verification().contains("баланс не изменился"));
        assertEquals(1, result.artifacts().quality().validatedFindings());
        assertEquals(1, result.artifacts().quality().validatedActions());
        assertFalse(result.artifacts().quality().warnings().isEmpty());
        assertTrue(result.artifacts().quality().warnings().contains("Поведение в production не проверялось."));
    }

    @Test void rejectsInventedMetric() { reject(valid().replace("\"totalCommits\":1", "\"totalCommits\":99")); }
    @Test void rejectsMissingMetric() {
        var mapper = new ObjectMapper();
        var response = mapper.readTree(valid());
        ((ObjectNode) response.path("metrics")).remove("authorCount");
        reject(mapper.writeValueAsString(response));
    }
    @Test void rejectsInventedCountInProseEvenWhenMetricObjectIsCorrect() {
        reject(valid().replace("Удалена проверка положительной суммы в PaymentService.", "Всего 99 коммитов за период."));
    }
    @Test void rejectsSourceOutsideReportPeriod() { reject(valid().replace("C101", "C999")); }
    @Test void rejectsUnknownCommitMention() {
        reject(valid().replace("Удалена проверка положительной суммы в PaymentService.", "Удалена проверка (bbbbbbb)."));
    }
    @Test void rejectsFindingWithoutEvidence() { reject(valid().replace("\"evidenceIds\":[\"C101\"]", "\"evidenceIds\":[]")); }
    @Test void rejectsInventedActionPath() { reject(valid().replace(FinalReportFixture.path(), "src/Fiction.java")); }
    @Test void rejectsGenericAcceptanceCriteria() {
        reject(valid().replace("Отправить сумму -1: запрос отклонён, баланс не изменился.", "Проверить тестами"));
    }
    @Test void rejectsTruncatedOrNonJsonResponses() { reject(valid().substring(0, valid().length() / 2)); reject("## Главное\nнет JSON"); }
    @Test void rejectsWrongRoleStructure() { reject(FinalReportFixture.response(ReportAudience.DIRECTOR)); }
    @Test void toleratesThinkAndCodeFenceButRemovesModelSuppliedLinks() {
        var result = service.parse("<think>ignore</think>```json\n" + valid().replace("Удалена проверка положительной суммы в PaymentService.",
                "Удалена [проверка](https://invented.example/secret).") + "\n```",
                FinalReportFixture.report(role), FinalReportFixture.facts(), FinalReportFixture.evidence());
        assertFalse(result.content().contains("invented.example"));
        assertFalse(result.content().contains("ignore"));
        assertTrue(result.content().contains("[C101](#evidence-C101)"));
    }
    private String valid() { return FinalReportFixture.response(role); }
    private void reject(String value) {
        assertThrows(IllegalStateException.class, () -> service.parse(value, FinalReportFixture.report(role),
                FinalReportFixture.facts(), FinalReportFixture.evidence()));
    }
}
