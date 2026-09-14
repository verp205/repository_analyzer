package com.ivan_naumenko.repository_analyzer.evaluation;

import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightParser;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightPromptFactory;
import com.ivan_naumenko.repository_analyzer.llm.client.LlmClient;
import com.ivan_naumenko.repository_analyzer.llm.client.OpenAiCompatibleLlmTransport;
import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.llm.context.service.CompactContextSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Оценка качества на реальной модели. В обычной сборке не запускается.
 * Команда запуска и переменные окружения описаны в README.
 */
@Tag("llm-evaluation")
class CommitInsightQualityEvaluationTest {

    @Test
    void modelMeetsQualityThresholds() throws Exception {
        String model = setting("LLM_EVAL_MODEL", "qwen3:8b");
        LlmProperties properties = new LlmProperties(
                "openai-compatible",
                "evaluation",
                setting("LLM_EVAL_BASE_URL", "http://127.0.0.1:11434"),
                setting("LLM_EVAL_CHAT_PATH", "/v1/chat/completions"),
                model,
                setting("LLM_EVAL_API_KEY", ""),
                1,
                700,
                setting("LLM_EVAL_REASONING_EFFORT", "none"),
                600,
                "none",
                1_800,
                "low",
                2_400,
                Boolean.parseBoolean(setting("LLM_EVAL_JSON_MODE", "true")),
                2,
                1_000,
                10_000,
                300_000,
                600_000
        );
        double minJson = Double.parseDouble(setting("LLM_EVAL_MIN_JSON", "0.95"));
        double minCategory = Double.parseDouble(setting("LLM_EVAL_MIN_CATEGORY", "0.70"));
        double minRisk = Double.parseDouble(setting("LLM_EVAL_MIN_RISK", "0.60"));
        double minRiskWithinOne = Double.parseDouble(setting("LLM_EVAL_MIN_RISK_WITHIN_ONE", "0.90"));

        JsonMapper mapper = JsonMapper.builder().build();
        LlmClient client = new LlmClient(new OpenAiCompatibleLlmTransport(properties, mapper), properties);
        CommitInsightEvaluator evaluator = new CommitInsightEvaluator(
                new CommitInsightPromptFactory(new CompactContextSerializer(mapper)),
                new CommitInsightParser(mapper)
        );

        EvaluationReport report = evaluator.evaluate(
                model,
                GoldenDataset.load(),
                prompt -> client.generate(prompt, LlmRequestProfile.COMMIT_ANALYSIS)
        );

        Path output = Path.of("target", "llm-evaluation");
        Files.createDirectories(output);
        Files.writeString(output.resolve("report.md"), report.toMarkdown());
        Files.writeString(
                output.resolve("report.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report)
        );
        System.out.println(report.toMarkdown());

        assertAll(
                () -> assertTrue(report.jsonValidRate() >= minJson,
                        "Корректный JSON: " + report.jsonValidRate() + " < " + minJson),
                () -> assertTrue(report.categoryAccuracy() >= minCategory,
                        "Категория: " + report.categoryAccuracy() + " < " + minCategory),
                () -> assertTrue(report.riskAccuracy() >= minRisk,
                        "Риск: " + report.riskAccuracy() + " < " + minRisk),
                () -> assertTrue(report.riskWithinOneAccuracy() >= minRiskWithinOne,
                        "Риск ±1: " + report.riskWithinOneAccuracy() + " < " + minRiskWithinOne)
        );
    }

    private static String setting(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
