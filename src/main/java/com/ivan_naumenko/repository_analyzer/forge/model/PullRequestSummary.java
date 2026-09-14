package com.ivan_naumenko.repository_analyzer.forge.model;

public record PullRequestSummary(
        int number,
        String title,
        String body,
        String url
) {
}
