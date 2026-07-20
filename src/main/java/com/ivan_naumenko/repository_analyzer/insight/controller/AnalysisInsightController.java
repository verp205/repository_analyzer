package com.ivan_naumenko.repository_analyzer.insight.controller;

import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.CommitClustersResponse;
import com.ivan_naumenko.repository_analyzer.insight.cluster.service.CommitClusteringService;
import com.ivan_naumenko.repository_analyzer.insight.graph.dto.CommitGraphResponse;
import com.ivan_naumenko.repository_analyzer.insight.graph.service.CommitGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analysis-tasks/{taskId}")
public class AnalysisInsightController {

    private final CommitGraphService graphService;
    private final CommitClusteringService clusteringService;

    @GetMapping("/graph")
    public CommitGraphResponse graph(@PathVariable Integer taskId) {
        return graphService.build(taskId);
    }

    @GetMapping("/clusters")
    public CommitClustersResponse clusters(@PathVariable Integer taskId) {
        return clusteringService.cluster(taskId);
    }
}
