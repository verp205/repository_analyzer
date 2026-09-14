package com.ivan_naumenko.repository_analyzer.report.sse;

import com.ivan_naumenko.repository_analyzer.commitinsight.dto.CommitInsightResponse;

import java.time.LocalDateTime;
import java.util.List;

public record ReportEvent(
        String type,
        Integer reportId,
        String status,
        int processedCommits,
        int totalCommits,
        String message,
        List<CommitInsightResponse> commits,
        LocalDateTime timestamp
) {

    public ReportEvent {
        commits = commits == null ? List.of() : List.copyOf(commits);
    }
}
