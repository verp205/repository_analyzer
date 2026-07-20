package com.ivan_naumenko.repository_analyzer.report.sse;

import java.time.LocalDateTime;

public record ReportEvent(
        String type,
        Integer reportId,
        int processedCommits,
        int totalCommits,
        String message,
        String contentChunk,
        LocalDateTime timestamp
) {
}
