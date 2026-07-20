package com.ivan_naumenko.repository_analyzer.insight.graph.dto;

public record CommitGraphEdge(
        String parentHash,
        String childHash,
        int parentPosition
) {
}
