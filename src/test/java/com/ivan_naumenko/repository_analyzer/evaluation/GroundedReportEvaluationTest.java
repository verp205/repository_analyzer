package com.ivan_naumenko.repository_analyzer.evaluation;

import com.ivan_naumenko.repository_analyzer.llm.client.*;
import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.Citation;
import com.ivan_naumenko.repository_analyzer.report.grounding.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Semantic regression set: expected risk and concrete scenario, not just valid JSON/source IDs. */
@Tag("llm-evaluation")
class GroundedReportEvaluationTest {
    @Test void rechecksRisksAgainstCodeAcrossLanguages() throws Exception {
        String model = setting("LLM_EVAL_MODEL", "qwen3:8b");
        var properties = new LlmProperties("openai-compatible", "evaluation", setting("LLM_EVAL_BASE_URL", "http://127.0.0.1:11434"),
                setting("LLM_EVAL_CHAT_PATH", "/v1/chat/completions"), model, setting("LLM_EVAL_API_KEY", ""),
                1, 700, "none", 600, "none", 4096, "low", 2400, true, 1, 0, 10_000, 300_000, 600_000);
        var mapper = JsonMapper.builder().build();
        var client = new LlmClient(new OpenAiCompatibleLlmTransport(properties, mapper), properties);
        var prompts = new GroundedReviewPrompt(mapper); var parser = new GroundedReviewParser(mapper);
        List<Map<String, Object>> results = new ArrayList<>();
        for (var golden : GoldenDataset.load("/evaluation/multilanguage-golden.json")) {
            String selected = System.getProperty("grounded.eval.ids", "");
            if (!selected.isBlank() && !Arrays.asList(selected.split(",")).contains(golden.id())) continue;
            var files = golden.commit().files().stream().map(file -> new ReviewContext.SourceFile(file.path(), file.path(), file.changeType(), file.addedLines(), file.deletedLines(), file.patch())).toList();
            List<Citation> citations = new ArrayList<>();
            for (var file : files) citations.add(new Citation("D" + citations.size(), "C101", file.path(), golden.hash(), "DIFF", 1, file.patch().split("\n", -1).length, file.patch()));
            var context = new ReviewContext(101, golden.hash(), files, citations, files.size(), 0, 0, List.of("Доступен только diff."));
            String original = prompts.build(context, null), prompt = original;
            List<String> responses = new ArrayList<>(); GroundedReviewParser.Review review = null;
            long start = System.nanoTime(); String error = "";
            for (int attempt = 0; attempt < 2 && review == null; attempt++) {
                try {
                    String raw = client.generate(prompt, LlmRequestProfile.GROUNDED_REVIEW); responses.add(raw);
                    var candidate = parser.parse(raw, context, "UNKNOWN");
                    if (!candidate.items().isEmpty()) {
                        String check = client.generate(prompts.verify(context, raw), LlmRequestProfile.GROUNDED_VERIFY);
                        responses.add("VERIFIER: " + check);
                        var verdict = parser.verdict(check);
                        if (!verdict.supported()) throw new IllegalStateException("Смысловая проверка: " + String.join("; ", verdict.issues()));
                    }
                    review = candidate;
                }
                catch (IllegalStateException invalid) {
                    error = invalid.getMessage();
                    prompt = original + "\nVALIDATION_FEEDBACK: " + error + "\nИсправь нарушение. Если данных недостаточно, верни пустой findings и объяснение в unknowns.";
                }
                catch (com.ivan_naumenko.repository_analyzer.llm.client.exception.LlmRequestException unavailable) {
                    error = unavailable.getMessage(); break;
                }
            }
            String risk = review == null ? "UNKNOWN" : review.items().stream().map(GroundedReviewParser.Item::riskLevel).max(Comparator.comparingInt(GroundedReportEvaluationTest::rank)).orElse("UNKNOWN");
            boolean riskCorrect = golden.expected().riskLevels().contains(risk);
            String scenario = review == null ? "" : review.items().stream().map(GroundedReviewParser.Item::verification).reduce("", (a, b) -> a + " " + b).toLowerCase(Locale.ROOT);
            boolean concrete = scenario.length() > 25 && terms(golden.id()).stream().anyMatch(scenario::contains);
            String impacts = review == null ? "" : review.items().stream().map(item -> item.title() + " " + item.impact() + " " + item.risk())
                    .reduce("", (a, b) -> a + " " + b).toLowerCase(Locale.ROOT);
            boolean semantic = review != null && !review.items().isEmpty() && switch (golden.id()) {
                case "python-negative-payment", "extensionless-python" -> impacts.contains("отриц") && !impacts.contains("ужесточение");
                case "rust-unchecked-index" -> (impacts.contains("panic") || impacts.contains("паник"))
                        && !impacts.contains("ошибке доступа к памяти") && !impacts.contains("повреждени");
                case "go-parameterized-query" -> impacts.contains("параметр") && !risk.equals("HIGH");
                default -> true;
            };
            results.add(Map.of("id", golden.id(), "risk", risk, "riskCorrect", riskCorrect, "concreteScenario", concrete,
                    "semanticCorrect", semantic, "responses", responses, "error", error, "milliseconds", (System.nanoTime() - start) / 1_000_000));
            Path output = Path.of("target/llm-evaluation/grounded-report.json"); Files.createDirectories(output.getParent());
            Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("model", model, "results", results)));
            System.out.println(golden.id() + ": risk=" + risk + ", expected=" + riskCorrect + ", scenario=" + concrete + ", meaning=" + semantic);
        }
        assertTrue(results.stream().allMatch(item -> item.get("riskCorrect").equals(true) && item.get("concreteScenario").equals(true)
                && item.get("semanticCorrect").equals(true)), mapper.writeValueAsString(results));
    }
    private static List<String> terms(String id) {
        return switch (id) {
            case "python-negative-payment", "extensionless-python" -> List.of("отриц", "сумм", "amount");
            case "typescript-auth-bypass" -> List.of("сесс", "401", "авториза", "аутентиф");
            case "go-parameterized-query" -> List.of("параметр", "инъек", "sql");
            case "rust-unchecked-index" -> List.of("индекс", "границ", "index");
            case "c-buffer-overflow" -> List.of("длин", "len", "буфер", "переполн");
            case "csharp-admin-access", "kotlin-access-check", "unknown-language" -> List.of("админ", "авториза", "доступ", "прав");
            case "php-html-escape" -> List.of("xss", "экранир", "html");
            case "ruby-token-logging" -> List.of("токен", "token", "лог");
            case "shell-quoted-path" -> List.of("кавыч", "пробел", "аргумент", "пут");
            default -> throw new IllegalArgumentException(id);
        };
    }
    private static int rank(String value) { return switch (value) { case "HIGH" -> 3; case "MEDIUM" -> 2; case "LOW" -> 1; default -> 0; }; }
    private static String setting(String name, String fallback) {
        String value = System.getenv(name); if (value == null || value.isBlank()) value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
