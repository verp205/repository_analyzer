package com.ivan_naumenko.repository_analyzer.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAnalysisTaskRequest(
        @NotBlank String repositoryUrl,
        @NotBlank String branch
) {
}
