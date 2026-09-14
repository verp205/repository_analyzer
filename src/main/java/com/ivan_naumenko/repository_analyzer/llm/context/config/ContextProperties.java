package com.ivan_naumenko.repository_analyzer.llm.context.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "repository-analyzer.context")
public record ContextProperties(
        @Min(1)
        int maxFiles,

        @Min(1)
        int maxPatchCharactersPerFile,

        @Min(1)
        int maxPatchCharactersTotal,

        @Min(1)
        int maxImportsPerFile,

        @Min(1)
        int maxParsingProblemsPerFile,

        @Min(1)
        int maxTypesPerFile,

        @Min(1)
        int maxMethodsPerType
) {
}
