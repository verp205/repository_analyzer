package com.ivan_naumenko.repository_analyzer.forge.model;

public record IssueContext(
        int number,
        String title,
        String url,
        String body
) {
}
