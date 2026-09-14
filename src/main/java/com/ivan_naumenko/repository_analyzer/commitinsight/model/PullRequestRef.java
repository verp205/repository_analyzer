package com.ivan_naumenko.repository_analyzer.commitinsight.model;

import java.util.List;

/** Краткая ссылка на pull/merge request, к которому относится коммит. */
public record PullRequestRef(
        int number,
        String title,
        String url,
        List<String> issueTitles
) {

    public PullRequestRef {
        title = title == null ? "" : title;
        issueTitles = issueTitles == null ? List.of() : List.copyOf(issueTitles);
    }
}
