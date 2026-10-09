package com.ivan_naumenko.repository_analyzer.evaluation;

import com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.Evidence;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.model.*;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FinalReportFixture {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final tools.jackson.databind.JsonNode DATA;
    static {
        try (var input = FinalReportFixture.class.getResourceAsStream("/evaluation/synthesis-golden.json")) {
            if (input == null) throw new IllegalStateException("Missing synthesis dataset");
            DATA = MAPPER.readTree(input);
        } catch (java.io.IOException exception) { throw new ExceptionInInitializerError(exception); }
    }
    private FinalReportFixture() {}
    public static String hash() { return DATA.path("commitHash").stringValue(); }
    public static String path() { return DATA.path("path").stringValue(); }
    public static String source() { return DATA.path("source").stringValue(); }
    public static RepositoryFactsResponse facts() { return MAPPER.treeToValue(DATA.path("facts"), RepositoryFactsResponse.class); }
    public static ReportGenerationData report(ReportAudience audience) {
        return new ReportGenerationData(9, 4, audience, facts().repositoryUrl(), "main", ReportPeriod.all(), 1, hash());
    }
    public static List<Evidence> evidence() {
        return List.of(new Evidence("F1", "FACTS", null, hash(), "Показатели", List.of(), MAPPER.writeValueAsString(facts())),
                new Evidence("C101", "COMMIT", 101, hash(), "Проверка суммы", List.of(path()), source()));
    }
    public static String response(ReportAudience audience) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("metrics", Map.of("totalCommits", 1, "authorCount", 1, "touchedFiles", 1, "addedLines", 2, "deletedLines", 1));
        root.put("summary", List.of(Map.of("text", "Удалена проверка положительной суммы в PaymentService.", "evidenceIds", List.of("C101"))));
        root.put("sections", AudienceProfile.of(audience).sections().stream().map(s -> Map.of(
                "title", s.title().replaceFirst("^##\\s*", ""), "findings", List.of())).toList());
        root.put("actions", List.of(Map.of("title", "Восстановить проверку положительной суммы", "reason", "Отрицательная сумма может увеличить баланс.",
                "verification", "Отправить сумму -1: запрос отклонён, баланс не изменился.", "priority", "HIGH", "evidenceIds", List.of("C101"), "paths", List.of(path()))));
        root.put("limitations", List.of("Поведение в production не проверялось."));
        return MAPPER.writeValueAsString(root);
    }
}
