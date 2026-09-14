package com.ivan_naumenko.repository_analyzer.commitinsight.model;

import java.util.List;

public record CommitInsightDetails(
        List<String> keyChanges,
        String behaviorImpact,
        List<String> risks,
        List<String> securityNotes,
        String testNotes,
        List<String> checks,
        PullRequestRef pullRequest
) {

    public CommitInsightDetails {
        keyChanges = keyChanges == null ? List.of() : List.copyOf(keyChanges);
        behaviorImpact = behaviorImpact == null ? "" : behaviorImpact;
        risks = risks == null ? List.of() : List.copyOf(risks);
        securityNotes = securityNotes == null
                ? List.of()
                : List.copyOf(securityNotes);
        testNotes = testNotes == null ? "" : testNotes;
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    public CommitInsightDetails(
            List<String> keyChanges,
            String behaviorImpact,
            List<String> risks,
            List<String> securityNotes,
            String testNotes,
            List<String> checks
    ) {
        this(keyChanges, behaviorImpact, risks, securityNotes, testNotes, checks, null);
    }

    public static CommitInsightDetails empty() {
        return new CommitInsightDetails(null, null, null, null, null, null, null);
    }

    public static CommitInsightDetails ofKeyChanges(List<String> keyChanges) {
        return new CommitInsightDetails(keyChanges, null, null, null, null, null, null);
    }

    public CommitInsightDetails withPullRequest(PullRequestRef reference) {
        return new CommitInsightDetails(
                keyChanges,
                behaviorImpact,
                risks,
                securityNotes,
                testNotes,
                checks,
                reference
        );
    }
}
