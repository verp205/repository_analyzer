package com.ivan_naumenko.repository_analyzer.insight.cluster.dto;

import com.ivan_naumenko.repository_analyzer.insight.cluster.model.ChangeClusterType;

import java.util.List;

public record ChangeCluster(
        ChangeClusterType type,
        String displayName,
        List<ClusteredCommit> commits
) {
}
