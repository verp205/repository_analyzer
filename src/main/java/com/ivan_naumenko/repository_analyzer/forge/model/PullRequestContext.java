package com.ivan_naumenko.repository_analyzer.forge.model;

import com.ivan_naumenko.repository_analyzer.commitinsight.model.PullRequestRef;

import java.util.List;

/** Описание, обсуждение и связанные задачи pull/merge request. */
public record PullRequestContext(
        ForgeProvider provider,
        int number,
        String title,
        String url,
        String body,
        List<String> comments,
        List<IssueContext> issues
) {

    public PullRequestContext {
        title = title == null ? "" : title;
        body = body == null ? "" : body;
        comments = comments == null ? List.of() : List.copyOf(comments);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public PullRequestRef toRef() {
        return new PullRequestRef(
                number,
                title,
                url,
                issues.stream().map(issue -> "#" + issue.number() + " " + issue.title()).toList()
        );
    }
}
