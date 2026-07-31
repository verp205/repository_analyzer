package com.ivan_naumenko.repository_analyzer.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAnalysisTaskRequest(
        @NotBlank String repositoryUrl,
        @NotBlank String branch,
        @Size(max = 255) String gitUsername,
        @Size(max = 4096) String gitAccessToken
) {
}
