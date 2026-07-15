package com.ivan_naumenko.repository_analyzer.analysis.exception.dto;

import java.time.LocalDateTime;

public record ApiErrorResponse(
        int status,
        String message,
        LocalDateTime timestamp
) {
}
