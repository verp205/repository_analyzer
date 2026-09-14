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
        return "stub:v3";
    }

    @Override
    public String generate(String prompt) {
        if (prompt.contains("TASK_TYPE: PROJECT_SYNTHESIS_FINAL")) {
            return """
                    ## Главное

                    - Это демонстрационный отчёт: реальная модель не подключена.
                    - История репозитория обработана, показатели рассчитаны системой.

                    ## О проекте

                    Смысловой анализ появится после подключения inference-сервера.

                    ## Рекомендации

                    - Укажите `repository-analyzer.llm.provider=openai-compatible`.

                    ## Ограничения анализа

                    Ответ сформирован демонстрационным transport.
                    """;
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
}
