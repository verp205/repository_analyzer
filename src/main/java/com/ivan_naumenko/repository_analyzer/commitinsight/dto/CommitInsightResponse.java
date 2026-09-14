package com.ivan_naumenko.repository_analyzer.commitinsight.dto;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.ChangeCategory;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightDetails;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.CommitInsightStatus;
import com.ivan_naumenko.repository_analyzer.commitinsight.model.RiskLevel;

import java.time.Instant;

public record CommitInsightResponse(
        Integer commitId,
        int position,
        String commitHash,
        String message,
        String authorName,
        Instant committedAt,
        int addedLines,
        int deletedLines,
        int changedFiles,
        CommitInsightStatus status,
        ChangeCategory category,
        String categoryName,
        RiskLevel riskLevel,
        String summary,
        CommitInsightDetails details
) {
}
