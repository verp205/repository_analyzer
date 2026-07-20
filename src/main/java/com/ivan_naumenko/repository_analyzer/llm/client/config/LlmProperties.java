package com.ivan_naumenko.repository_analyzer.llm.client.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "repository-analyzer.llm")
public record LlmProperties(
        boolean enabled,

        @NotBlank
        String baseUrl,

        @NotBlank
        String chatPath,

        @NotBlank
        String model,

        String apiKey,

        @Min(1)
        @Max(10)
        int maxConcurrentRequests
) {
}
