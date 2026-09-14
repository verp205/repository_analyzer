package com.ivan_naumenko.repository_analyzer.evaluation;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public record EvaluationReport(
        String model,
        List<CaseResult> results
) {

    public record CaseResult(
            String id,
            boolean jsonValid,
            String category,
            String riskLevel,
            GoldenCommitCase.Expected expected,
            boolean categoryCorrect,
            boolean riskCorrect,
            boolean riskWithinOne,
            long durationMillis,
            String error
    ) {
    }

    public double jsonValidRate() {
        return rate(CaseResult::jsonValid);
    }

    public double categoryAccuracy() {
        return rate(CaseResult::categoryCorrect);
    }

    public double riskAccuracy() {
        return rate(CaseResult::riskCorrect);
    }

    /** Риск угадан точно или ошибка не больше чем на один уровень. */
    public double riskWithinOneAccuracy() {
        return rate(CaseResult::riskWithinOne);
    }

    public long averageDurationMillis() {
        return results.isEmpty()
                ? 0
                : Math.round(results.stream().mapToLong(CaseResult::durationMillis).average().orElse(0));
    }

    public String toMarkdown() {
        StringBuilder markdown = new StringBuilder()
                .append("# Оценка качества разбора коммитов\n\n")
                .append("Модель: ").append(model).append("  \n")
                .append("Кейсов: ").append(results.size()).append("\n\n")
                .append("| Метрика | Значение |\n|---|---|\n")
                .append("| Корректный JSON | ").append(percent(jsonValidRate())).append(" |\n")
                .append("| Категория | ").append(percent(categoryAccuracy())).append(" |\n")
                .append("| Риск точно | ").append(percent(riskAccuracy())).append(" |\n")
                .append("| Риск ±1 уровень | ").append(percent(riskWithinOneAccuracy())).append(" |\n")
                .append("| Среднее время ответа | ").append(averageDurationMillis()).append(" мс |\n");

        List<CaseResult> mistakes = results.stream()
                .filter(result -> !result.categoryCorrect() || !result.riskCorrect())
                .toList();
        if (!mistakes.isEmpty()) {
            markdown.append("\n## Расхождения\n\n")
                    .append("| Кейс | Категория (ожидалось) | Риск (ожидалось) | Ошибка |\n|---|---|---|---|\n");
            for (CaseResult result : mistakes) {
                markdown.append("| ").append(result.id())
                        .append(" | ").append(value(result.category()))
                        .append(" (").append(String.join(", ", result.expected().categories())).append(")")
                        .append(" | ").append(value(result.riskLevel()))
                        .append(" (").append(String.join(", ", result.expected().riskLevels())).append(")")
                        .append(" | ").append(result.error() == null ? "" : result.error().replace("|", "/"))
                        .append(" |\n");
            }
        }
        return markdown.toString();
    }

    private double rate(Predicate<CaseResult> predicate) {
        return results.isEmpty()
                ? 0
                : (double) results.stream().filter(predicate).count() / results.size();
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }

    private static String value(String value) {
        return value == null ? "—" : value;
    }
}
