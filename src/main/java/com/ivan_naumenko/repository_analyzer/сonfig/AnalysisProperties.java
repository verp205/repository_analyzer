package com.ivan_naumenko.repository_analyzer.сonfig;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "repository-analyzer.analysis")
public record AnalysisProperties(
        @Min(1)
        @Max(10)
        int maxThreads
) {
}
