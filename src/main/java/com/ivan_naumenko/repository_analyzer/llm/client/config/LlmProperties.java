package com.ivan_naumenko.repository_analyzer.llm.client.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestOptions;
import com.ivan_naumenko.repository_analyzer.llm.client.model.LlmRequestProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;
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

        String synthesisFinalReasoningEffort
) {

    public LlmProperties {
        new LlmRequestOptions(commitMaxTokens, commitReasoningEffort);
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
                    commitReasoningEffort
            );
            case SYNTHESIS_BATCH -> new LlmRequestOptions(
                    synthesisBatchMaxTokens,
                    synthesisBatchReasoningEffort
            );
            case SYNTHESIS_FINAL -> new LlmRequestOptions(
                    synthesisFinalMaxTokens,
                    synthesisFinalReasoningEffort
            );
        };
    }
}
