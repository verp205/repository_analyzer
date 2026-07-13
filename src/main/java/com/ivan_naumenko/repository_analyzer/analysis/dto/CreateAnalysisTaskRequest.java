package com.ivan_naumenko.repository_analyzer.analysis.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAnalysisTaskRequest(
        @NotBlank String repositoryUrl,
        @NotBlank String branch
) {
}
