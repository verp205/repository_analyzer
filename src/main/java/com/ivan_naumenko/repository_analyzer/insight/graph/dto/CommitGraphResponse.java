package com.ivan_naumenko.repository_analyzer.insight.graph.dto;

import java.util.List;

public record CommitGraphResponse(
        Integer analysisTaskId,
        int totalCommits,
        int rootCount,
        int tipCount,
        int mergeCommitCount,
        int maxDepth,
        int missingParentCount,
        List<CommitGraphNode> nodes,
        List<CommitGraphEdge> edges
) {
}
