package com.ivan_naumenko.repository_analyzer.llm.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(
        prefix = "repository-analyzer.llm",
        name = "provider",
        havingValue = "stub",
        matchIfMissing = true
)
public class StubLlmTransport implements LlmTransport {

    private static final Pattern COMMIT_HASH = Pattern.compile(
            "\"commitHash\"\\s*:\\s*\"([0-9a-fA-F]{7})"
    );
    private static final String COMMIT_FIELDS = """
            "summary":"Демонстрационный анализ коммита: подключите LLM для реальных выводов.",\
            "category":"CHORE","riskLevel":"LOW",\
            "keyChanges":["Демонстрационный transport не читает изменения"],\
            "behaviorImpact":"","risks":[],"securityNotes":[],"testNotes":"","checks":[]""";

    @Override
    public String cacheNamespace() {
        return "stub:v4";
    }

    @Override
    public String generate(String prompt) {
        if (prompt.contains("TASK_TYPE: GROUNDED_REVIEW")) {
            return "{\"findings\":[],\"unknowns\":[\"Демонстрационный режим: смысловая проверка кода не выполнялась.\"]}";
        }
        if (prompt.contains("TASK_TYPE: PROJECT_SYNTHESIS_FINAL")) {
            var mapper = new tools.jackson.databind.ObjectMapper();
            String metrics = capture(prompt, "FACT_METRICS: (\\{[^\\n]+})");
            String titles = capture(prompt, "SECTION_TITLES_JSON: (\\[[^\\n]+])");
            var sections = new java.util.ArrayList<java.util.Map<String, Object>>();
            for (var title : mapper.readTree(titles)) {
                sections.add(java.util.Map.of("title", title.stringValue(), "findings", List.of()));
            }
            return mapper.writeValueAsString(java.util.Map.of(
                    "metrics", mapper.readTree(metrics),
                    "summary", List.of(java.util.Map.of("text", "Демонстрационный отчёт: показатели рассчитаны системой, смысловая оценка не выполнялась.", "evidenceIds", List.of("F1"))),
                    "sections", sections,
                    "actions", List.of(),
                    "limitations", List.of("Ответ сформирован демонстрационным transport, реальная модель не подключена.")));

        }
        if (prompt.contains("TASK_TYPE: PROJECT_SYNTHESIS_BATCH")) {
            return """
                    - Сводка группы коммитов подготовлена для итогового синтеза.
                    - Ограничение: используется демонстрационный transport.
                    """;
        }
        if (prompt.contains("TASK_TYPE: COMMIT_INSIGHT_BATCH")) {
            List<String> items = new ArrayList<>();
            Matcher matcher = COMMIT_HASH.matcher(prompt);
            while (matcher.find()) {
                items.add("{\"hash\":\"%s\",%s}".formatted(
                        matcher.group(1),
                        COMMIT_FIELDS
                ));
            }
            return "{\"commits\":[" + String.join(",", items) + "]}";
        }
        if (prompt.contains("TASK_TYPE: COMMIT_INSIGHT")) {
            return "{" + COMMIT_FIELDS + "}";
        }
        return "Демонстрационный ответ inference-сервера.";
    }
    private String capture(String prompt, String pattern) {
        var matcher = Pattern.compile(pattern).matcher(prompt);
        if (!matcher.find()) throw new IllegalArgumentException("Нет данных для демонстрационного отчёта");
        return matcher.group(1);
    }
}
