package com.ivan_naumenko.repository_analyzer.evaluation;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDraft;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.*;
import com.ivan_naumenko.repository_analyzer.llm.client.*;
import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.llm.context.service.CompactContextSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Paired real-model comparison: no passing property may become failing in an individual case. */
@Tag("llm-evaluation")
class MultilanguageQualityEvaluationTest {
    record Score(boolean jsonValid, boolean categoryCorrect, boolean riskCorrect, int riskDistance, boolean sourceMentioned,
                 boolean concreteChecks, long millis, String raw) {}
    record Pair(String id, Score before, Score after, List<String> regressions) {}

    @Test void universalReviewDoesNotRegressAgainstOriginalPrompt() throws Exception {
        String model = setting("LLM_EVAL_MODEL", "qwen3:8b");
        var properties = new LlmProperties("openai-compatible", "evaluation", setting("LLM_EVAL_BASE_URL", "http://127.0.0.1:11434"),
                setting("LLM_EVAL_CHAT_PATH", "/v1/chat/completions"), model, setting("LLM_EVAL_API_KEY", ""),
                1, 700, "none", 600, "none", 4096, "low", 2400, true, 1, 0, 10_000, 300_000, 600_000);
        var mapper = JsonMapper.builder().build();
        var client = new LlmClient(new OpenAiCompatibleLlmTransport(properties, mapper), properties);
        var serializer = new CompactContextSerializer(mapper);
        var legacy = new LegacyCommitInsightPromptFactory(serializer);
        var candidate = new CommitInsightPromptFactory(serializer);
        var parser = new CommitInsightParser(mapper);
        List<Pair> results = new ArrayList<>();
        for (var golden : GoldenDataset.load("/evaluation/multilanguage-golden.json")) {
            var before = evaluate(golden, legacy.buildSingle(golden.toContext()), client, parser);
            var after = evaluate(golden, candidate.buildSingle(PromptCompatibilityTest.withLanguages(golden.toContext())), client, parser);
            List<String> regressions = new ArrayList<>();
            check(regressions, "JSON", before.jsonValid, after.jsonValid);
            check(regressions, "category", before.categoryCorrect, after.categoryCorrect);
            check(regressions, "risk", before.riskCorrect, after.riskCorrect);
            if (after.riskDistance > before.riskDistance) regressions.add("riskDistance");
            check(regressions, "source", before.sourceMentioned, after.sourceMentioned);
            check(regressions, "checks", before.concreteChecks, after.concreteChecks);
            results.add(new Pair(golden.id(), before, after, regressions));
            System.out.println(golden.id() + ": " + regressions);
        }
        Path output = Path.of("target", "llm-evaluation");
        Files.createDirectories(output);
        Files.writeString(output.resolve("multilanguage-report.json"), mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(Map.of("model", model, "results", results)));
        StringBuilder markdown = new StringBuilder("# Парное сравнение текстового анализа\n\nМодель: " + model
                + "\n\n| Кейс | JSON до/после | Категория | Риск | Источник | Проверки | Регрессии |\n|---|---|---|---|---|---|---|\n");
        for (var pair : results) markdown.append("| ").append(pair.id).append(" | ")
                .append(pair.before.jsonValid).append('/').append(pair.after.jsonValid).append(" | ")
                .append(pair.before.categoryCorrect).append('/').append(pair.after.categoryCorrect).append(" | ")
                .append(pair.before.riskCorrect).append('/').append(pair.after.riskCorrect).append(" | ")
                .append(pair.before.sourceMentioned).append('/').append(pair.after.sourceMentioned).append(" | ")
                .append(pair.before.concreteChecks).append('/').append(pair.after.concreteChecks).append(" | ")
                .append(String.join(", ", pair.regressions)).append(" |\n");
        Files.writeString(output.resolve("multilanguage-report.md"), markdown.toString());
        assertTrue(results.stream().allMatch(pair -> pair.regressions.isEmpty()), markdown.toString());
        assertTrue(results.stream().allMatch(pair -> pair.after.jsonValid), "Every candidate response must parse");
    }

    private Score evaluate(GoldenCommitCase golden, String prompt, LlmClient client, CommitInsightParser parser) {
        long started = System.nanoTime();
        String raw = client.generate(prompt, LlmRequestProfile.COMMIT_ANALYSIS);
        var parsed = parser.parseSingle(raw);
        String path = golden.commit().files().getFirst().path();
        String name = path.substring(path.lastIndexOf('/') + 1);
        return new Score(parsed.isPresent(), parsed.map(d -> golden.expected().categories().contains(d.category().name())).orElse(false),
                parsed.map(d -> golden.expected().riskLevels().contains(d.riskLevel().name())).orElse(false),
                parsed.map(d -> golden.expected().riskLevels().stream().mapToInt(expected -> Math.abs(rank(expected) - rank(d.riskLevel().name()))).min().orElse(99)).orElse(99),
                parsed.map(d -> (d.summary() + " " + d.details()).contains(name)).orElse(false),
                parsed.map(d -> groundedChecks(golden.id(), d.details().checks())).orElse(false),
                (System.nanoTime() - started) / 1_000_000, raw);
    }
    private static void check(List<String> regressions, String name, boolean before, boolean after) {
        if (before && !after) regressions.add(name);
    }
    private static int rank(String risk) {
        return switch (risk) { case "NONE" -> 0; case "LOW" -> 1; case "MEDIUM" -> 2; case "HIGH" -> 3; default -> 99; };
    }

    private static boolean groundedChecks(String id, List<String> checks) {
        List<String> terms = switch (id) {
            case "python-negative-payment", "extensionless-python" -> List.of("отриц", "сумм", "amount");
            case "typescript-auth-bypass" -> List.of("сесс", "401", "авториза", "аутентиф");
            case "go-parameterized-query" -> List.of("параметр", "инъек", "sql");
            case "rust-unchecked-index" -> List.of("индекс", "границ", "index");
            case "c-buffer-overflow" -> List.of("длин", "len", "буфер", "переполн");
            case "csharp-admin-access" -> List.of("аноним", "авториза", "аутентиф", "админ", "доступ");
            case "php-html-escape" -> List.of("xss", "экранир", "html");
            case "ruby-token-logging" -> List.of("токен", "token", "лог");
            case "kotlin-access-check" -> List.of("админ", "isadmin", "доступ");
            case "shell-quoted-path" -> List.of("кавыч", "пробел", "аргумент", "пут", "cache", "rm");
            case "unknown-language" -> List.of("доступ", "прав", "refund", "авториза", "возврат");
            default -> throw new IllegalArgumentException("Missing grounded-check terms for " + id);
        };
        return checks.stream().anyMatch(check -> check.length() >= 25
                && terms.stream().anyMatch(check.toLowerCase(Locale.ROOT)::contains));
    }
    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
