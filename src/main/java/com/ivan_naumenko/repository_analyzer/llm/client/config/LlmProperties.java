package com.ivan_naumenko.repository_analyzer.llm.client.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestOptions;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "repository-analyzer.llm")
public record LlmProperties(
        @NotBlank
        String provider,

        @NotBlank
        String providerId,

        @NotBlank
        String baseUrl,

        @NotBlank
        String chatPath,

        @NotBlank
        String model,

        String apiKey,

        @Min(1)
        @Max(10)
        int maxConcurrentRequests,

        @Min(128)
        @Max(4_096)
        int commitMaxTokens,

        String commitReasoningEffort,

        @Min(128)
        @Max(4_096)
        int synthesisBatchMaxTokens,

        String synthesisBatchReasoningEffort,

        @Min(256)
        @Max(8_192)
        int synthesisFinalMaxTokens,

        String synthesisFinalReasoningEffort,

        @DefaultValue("2400")
        @Min(256)
        @Max(8_192)
        int commitBatchMaxTokens,

        @DefaultValue("true")
        boolean jsonMode,

        @DefaultValue("3")
        @Min(1)
        @Max(5)
        int maxAttempts,

        @DefaultValue("2000")
        @Min(0)
        @Max(60_000)
        long retryBackoffMillis,

        /* Время на установку соединения с inference-сервером. */
        @DefaultValue("10000")
        @Min(100)
        long connectTimeoutMillis,

        /* Время на ответ по одному коммиту; для пакета коммитов удваивается. */
        @DefaultValue("180000")
        @Min(1_000)
        long commitTimeoutMillis,

        /* Время на промежуточную и итоговую сводку. */
        @DefaultValue("600000")
        @Min(1_000)
        long synthesisTimeoutMillis
) {

    public LlmProperties {
        new LlmRequestOptions(commitMaxTokens, commitReasoningEffort);
        new LlmRequestOptions(commitBatchMaxTokens, commitReasoningEffort);
        new LlmRequestOptions(
                synthesisBatchMaxTokens,
                synthesisBatchReasoningEffort
        );
        new LlmRequestOptions(
                synthesisFinalMaxTokens,
                synthesisFinalReasoningEffort
        );
    }

    public LlmRequestOptions optionsFor(LlmRequestProfile profile) {
        return switch (profile) {
            case COMMIT_ANALYSIS -> new LlmRequestOptions(
                    commitMaxTokens,
                    commitReasoningEffort,
                    jsonMode,
                    commitTimeoutMillis
            );
            case COMMIT_BATCH -> new LlmRequestOptions(
                    commitBatchMaxTokens,
                    commitReasoningEffort,
                    jsonMode,
                    commitTimeoutMillis * 2
            );
            case SYNTHESIS_BATCH -> new LlmRequestOptions(
                    synthesisBatchMaxTokens,
                    synthesisBatchReasoningEffort,
                    false,
                    synthesisTimeoutMillis
            );
            case SYNTHESIS_FINAL -> new LlmRequestOptions(
                    synthesisFinalMaxTokens,
                    synthesisFinalReasoningEffort,
                    false,
                    synthesisTimeoutMillis
            );
        };
    }
}
