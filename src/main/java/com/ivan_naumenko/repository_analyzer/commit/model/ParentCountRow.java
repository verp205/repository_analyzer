package com.ivan_naumenko.repository_analyzer.commit.model;

public record ParentCountRow(
        Integer commitId,
        Long parentCount
) {
}
