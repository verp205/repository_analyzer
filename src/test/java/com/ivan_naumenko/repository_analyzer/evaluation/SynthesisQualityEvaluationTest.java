package com.ivan_naumenko.repository_analyzer.evaluation;

import com.ivan_naumenko.repository_analyzer.llm.client.*;
import com.ivan_naumenko.repository_analyzer.llm.client.config.LlmProperties;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import com.ivan_naumenko.repository_analyzer.llm.prompt.service.ProjectSynthesisPromptFactory;
import com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifactService;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.snapshot.RepositorySnapshot;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in evaluation of the final report on an inspectable, fixed source case for every audience. */
@Tag("llm-evaluation")
class SynthesisQualityEvaluationTest {
    @Test void finalReportsMeetGroundingThreshold() throws Exception {
        String model = setting("LLM_EVAL_MODEL", "qwen3:8b");
        var properties = new LlmProperties("openai-compatible", "evaluation",
                setting("LLM_EVAL_BASE_URL", "http://127.0.0.1:11434"), setting("LLM_EVAL_CHAT_PATH", "/v1/chat/completions"),
                model, setting("LLM_EVAL_API_KEY", ""), 1, 700, "none", 600, "none", 4096,
                setting("LLM_EVAL_SYNTHESIS_REASONING_EFFORT", "low"), 2400,
                Boolean.parseBoolean(setting("LLM_EVAL_JSON_MODE", "true")), 1, 0, 10_000, 300_000, 600_000);
        var mapper = JsonMapper.builder().build();
        var client = new LlmClient(new OpenAiCompatibleLlmTransport(properties, mapper), properties);
        var artifacts = new ReportArtifactService(mapper);
        var prompts = new ProjectSynthesisPromptFactory();
        var evidence = FinalReportFixture.evidence();
        List<Map<String, Object>> results = new ArrayList<>();
        int accepted = 0;
        String audienceFilter = setting("LLM_EVAL_AUDIENCES", "");
        List<ReportAudience> audiences = audienceFilter.isBlank() ? List.of(ReportAudience.values())
                : java.util.Arrays.stream(audienceFilter.split(",")).map(String::strip).map(ReportAudience::valueOf).toList();
        for (ReportAudience audience : audiences) {
            String raw = "";
            long started = System.nanoTime();
            List<String> responses = new ArrayList<>();
            try {
                String prompt = prompts.buildFinal(FinalReportFixture.report(audience), FinalReportFixture.facts(),
                        RepositorySnapshot.unavailable(), List.of(FinalReportFixture.source()),
                        artifacts.promptCatalog(evidence, List.of(FinalReportFixture.source())));
                var result = com.ivan_naumenko.repository_analyzer.report.service.ValidatedSynthesis.generate(prompt,
                        FinalReportFixture.report(audience), FinalReportFixture.facts(), evidence, artifacts,
                        request -> { String response = client.generate(request, LlmRequestProfile.SYNTHESIS_FINAL); responses.add(response); return response; },
                        (request, response) -> {});
                raw = responses.getLast();
                // The known source contains a concrete validation regression. Do not accept an empty plan.
                boolean hasGroundedAction = result.artifacts().actions().stream().anyMatch(action ->
                        action.evidenceIds().contains("C101") && action.paths().contains(FinalReportFixture.path()));
                boolean mentionsAmount = result.content().toLowerCase().contains("сумм")
                        || result.artifacts().actions().stream().anyMatch(action -> action.title().toLowerCase().contains("сумм"));
                if (hasGroundedAction && mentionsAmount) accepted++;
                results.add(Map.of("audience", audience, "accepted", hasGroundedAction && mentionsAmount,
                        "content", result.content(), "artifacts", result.artifacts(), "raw", raw, "attempts", responses.size(),
                        "millis", (System.nanoTime() - started) / 1_000_000));
            } catch (RuntimeException exception) {
                raw = responses.isEmpty() ? "" : responses.getLast();
                results.add(Map.of("audience", audience, "accepted", false, "error", exception.getMessage(), "raw", raw,
                        "attempts", responses.size(), "millis", (System.nanoTime() - started) / 1_000_000));
            }
            System.out.println(audience + ": " + results.getLast().get("accepted") + ", calls=" + responses.size());
        }
        double rate = (double) accepted / results.size();
        Path output = Path.of("target", "llm-evaluation");
        Files.createDirectories(output);
        String outputName = audienceFilter.isBlank() ? "synthesis-report"
                : "synthesis-report-" + audiences.stream().map(a -> a.name().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.joining("-"));
        Files.writeString(output.resolve(outputName + ".json"), mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(Map.of("model", model, "acceptedRate", rate, "results", results)));
        StringBuilder markdown = new StringBuilder("# Оценка итогового синтеза\n\nМодель: " + model
                + "\n\nОтчёты с корректными числами, ссылками, структурой и обоснованной задачей: " + accepted + "/" + results.size() + "\n\n");
        results.forEach(result -> markdown.append("## ").append(result.get("audience")).append("\n\n")
                .append(result.getOrDefault("content", result.getOrDefault("error", ""))).append("\n\n"));
        Files.writeString(output.resolve(outputName + ".md"), markdown.toString());
        assertTrue(rate >= Double.parseDouble(setting("LLM_EVAL_MIN_SYNTHESIS", "1.0")), "Итоговый синтез: " + rate);
    }
    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
