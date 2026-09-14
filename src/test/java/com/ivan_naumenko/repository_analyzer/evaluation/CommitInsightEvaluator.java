package com.ivan_naumenko.repository_analyzer.evaluation;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDraft;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.RiskLevel;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightParser;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightPromptFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Прогоняет эталонные коммиты через тот же промпт и парсер, что и
 * приложение, и сравнивает категорию и риск с ожидаемыми.
 */
public class CommitInsightEvaluator {

    private final CommitInsightPromptFactory promptFactory;
    private final CommitInsightParser parser;

    public CommitInsightEvaluator(CommitInsightPromptFactory promptFactory, CommitInsightParser parser) {
        this.promptFactory = promptFactory;
        this.parser = parser;
    }

    public EvaluationReport evaluate(
            String model,
            List<GoldenCommitCase> cases,
            Function<String, String> llm
    ) {
        List<EvaluationReport.CaseResult> results = new ArrayList<>(cases.size());
        for (GoldenCommitCase goldenCase : cases) {
            long started = System.nanoTime();
            String response = null;
            String error = null;
            try {
                response = llm.apply(promptFactory.buildSingle(goldenCase.toContext()));
            } catch (RuntimeException exception) {
                error = exception.getMessage();
            }
            long durationMillis = (System.nanoTime() - started) / 1_000_000;

            Optional<CommitInsightDraft> draft = response == null
                    ? Optional.empty()
                    : parser.parseSingle(response);
            String category = draft.map(value -> value.category().name()).orElse(null);
            String risk = draft.map(value -> value.riskLevel().name()).orElse(null);
            GoldenCommitCase.Expected expected = goldenCase.expected();

            results.add(new EvaluationReport.CaseResult(
                    goldenCase.id(),
                    draft.isPresent(),
                    category,
                    risk,
                    expected,
                    category != null && expected.categories().contains(category),
                    risk != null && expected.riskLevels().contains(risk),
                    risk != null && withinOneLevel(risk, expected.riskLevels()),
                    durationMillis,
                    error
            ));
        }
        return new EvaluationReport(model, results);
    }

    static boolean withinOneLevel(String actual, List<String> expected) {
        int actualRank = rank(actual);
        return actualRank >= 0 && expected.stream()
                .mapToInt(CommitInsightEvaluator::rank)
                .anyMatch(expectedRank -> Math.abs(expectedRank - actualRank) <= 1);
    }

    private static int rank(String risk) {
        return switch (RiskLevel.parse(risk)) {
            case NONE -> 0;
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case UNKNOWN -> -1;
        };
    }
}
