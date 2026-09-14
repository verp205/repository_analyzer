package com.ivan_naumenko.repository_analyzer.commitinsight.model;

import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;

import java.util.List;

public record InsightWorkUnit(
        Kind kind,
        List<CommitChangeSummary> commits
) {

    public enum Kind {
        TEMPLATE,
        SINGLE,
        BATCH
    }

    public InsightWorkUnit {
        if (commits == null || commits.isEmpty()) {
            throw new IllegalArgumentException(
                    "Единица работы должна содержать коммиты"
            );
        }
        commits = List.copyOf(commits);
    }

    public static InsightWorkUnit single(CommitChangeSummary commit) {
        return new InsightWorkUnit(Kind.SINGLE, List.of(commit));
    }
}
