package com.ivan_naumenko.repository_analyzer.commitinsight.model;

import java.util.Objects;

public record CommitInsightDraft(
        CommitInsightStatus status,
        ChangeCategory category,
        RiskLevel riskLevel,
        String summary,
        CommitInsightDetails details
) {

    public CommitInsightDraft {
        Objects.requireNonNull(status, "status не должен быть null");
        category = category == null ? ChangeCategory.CHORE : category;
        riskLevel = riskLevel == null ? RiskLevel.UNKNOWN : riskLevel;
        summary = summary == null ? "" : summary.strip();
        details = details == null ? CommitInsightDetails.empty() : details;
    }
}
