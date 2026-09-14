package com.ivan_naumenko.repository_analyzer.evaluation;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.ChangeCategory;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.RiskLevel;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightParser;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightPromptFactory;
import com.ivan_naumenko.repository_analyzer.llm.context.service.CompactContextSerializer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверяет сам эталонный набор и оценщик без обращения к LLM. */
class GoldenDatasetTest {

    private final List<GoldenCommitCase> cases = GoldenDataset.load();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CommitInsightEvaluator evaluator = new CommitInsightEvaluator(
            new CommitInsightPromptFactory(new CompactContextSerializer(mapper)),
            new CommitInsightParser(mapper)
    );

    @Test
    void datasetIsWellFormedAndCoversCategoriesAndRisks() {
        assertTrue(cases.size() >= 20 && cases.size() <= 30, "Кейсов: " + cases.size());
        assertEquals(cases.size(), cases.stream().map(GoldenCommitCase::id).distinct().count());

        Set<String> primaryCategories = new HashSet<>();
        Set<String> primaryRisks = new HashSet<>();
        for (GoldenCommitCase goldenCase : cases) {
            assertFalse(goldenCase.expected().categories().isEmpty(), goldenCase.id());
            assertFalse(goldenCase.expected().riskLevels().isEmpty(), goldenCase.id());
            goldenCase.expected().categories().forEach(ChangeCategory::valueOf);
            goldenCase.expected().riskLevels().forEach(risk ->
                    assertFalse(RiskLevel.valueOf(risk) == RiskLevel.UNKNOWN, goldenCase.id()));
            assertFalse(goldenCase.commit().files().isEmpty(), goldenCase.id());
            assertEquals(40, goldenCase.toContext().commitHash().length());

            primaryCategories.add(goldenCase.expected().categories().getFirst());
            primaryRisks.add(goldenCase.expected().riskLevels().getFirst());
        }

        assertTrue(primaryCategories.size() >= 8, "Категории: " + primaryCategories);
        assertEquals(Set.of("NONE", "LOW", "MEDIUM", "HIGH"), primaryRisks);
    }

    @Test
    void perfectModelGetsFullMarks() {
        Map<String, GoldenCommitCase> byHash = cases.stream()
                .collect(Collectors.toMap(GoldenCommitCase::hash, Function.identity()));

        EvaluationReport report = evaluator.evaluate("oracle", cases, prompt -> {
            GoldenCommitCase goldenCase = byHash.entrySet().stream()
                    .filter(entry -> prompt.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow();
            return """
                    {"summary":"Эталон","category":"%s","riskLevel":"%s"}
                    """.formatted(
                    goldenCase.expected().categories().getFirst(),
                    goldenCase.expected().riskLevels().getFirst()
            );
        });

        assertEquals(1.0, report.jsonValidRate());
        assertEquals(1.0, report.categoryAccuracy());
        assertEquals(1.0, report.riskAccuracy());
        assertFalse(report.toMarkdown().contains("## Расхождения"));
    }

    @Test
    void brokenModelIsDetected() {
        EvaluationReport report = evaluator.evaluate("broken", cases, prompt -> "Это не JSON");

        assertEquals(0.0, report.jsonValidRate());
        assertEquals(0.0, report.categoryAccuracy());
        assertTrue(report.toMarkdown().contains("| Корректный JSON | 0% |"));
        assertTrue(report.toMarkdown().contains("## Расхождения"));
    }

    @Test
    void riskWithinOneLevelIsCountedSeparately() {
        assertTrue(CommitInsightEvaluator.withinOneLevel("MEDIUM", List.of("HIGH")));
        assertFalse(CommitInsightEvaluator.withinOneLevel("LOW", List.of("HIGH")));
        assertFalse(CommitInsightEvaluator.withinOneLevel("UNKNOWN", List.of("NONE")));
    }
}
