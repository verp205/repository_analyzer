package com.ivan_naumenko.repository_analyzer.insight.cluster.dto;

import java.util.List;

public record CommitClustersResponse(
        Integer analysisTaskId,
        int clusteredCommits,
        List<ChangeCluster> clusters
) {
}
