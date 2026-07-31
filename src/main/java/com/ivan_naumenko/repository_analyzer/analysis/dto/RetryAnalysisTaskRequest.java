package com.ivan_naumenko.repository_analyzer.analysis.dto;

import jakarta.validation.constraints.Size;

public record RetryAnalysisTaskRequest(
        @Size(max = 255) String gitUsername,
        @Size(max = 4096) String gitAccessToken
) {
}
