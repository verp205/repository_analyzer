package com.ivan_naumenko.repository_analyzer.insight.controller;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.commitinsight.dto.CommitInsightResponse;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightService;
import com.ivan_naumenko.repository_analyzer.git.exception.RepositoryNotFoundException;
import com.ivan_naumenko.repository_analyzer.git.model.GitTag;
import com.ivan_naumenko.repository_analyzer.git.service.GitService;
import com.ivan_naumenko.repository_analyzer.insight.cluster.dto.CommitClustersResponse;
import com.ivan_naumenko.repository_analyzer.insight.cluster.service.CommitClusteringService;
import com.ivan_naumenko.repository_analyzer.insight.graph.dto.CommitGraphResponse;
import com.ivan_naumenko.repository_analyzer.insight.graph.service.CommitGraphService;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.facts.service.RepositoryFactsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analysis-tasks/{taskId}")
public class AnalysisInsightController {

    private final CommitGraphService graphService;
    private final CommitClusteringService clusteringService;
    private final RepositoryFactsService factsService;
    private final CommitInsightService insightService;
    private final AnalysisTaskService taskService;
    private final GitService gitService;

    @GetMapping("/graph")
    public CommitGraphResponse graph(@PathVariable Integer taskId) {
        return graphService.build(taskId);
    }

    @GetMapping("/clusters")
    public CommitClustersResponse clusters(@PathVariable Integer taskId) {
        return clusteringService.cluster(taskId);
    }

    @GetMapping("/facts")
    public RepositoryFactsResponse facts(@PathVariable Integer taskId) {
        return factsService.build(taskId);
    }

    @GetMapping("/commit-insights")
    public List<CommitInsightResponse> commitInsights(
            @PathVariable Integer taskId
    ) {
        taskService.getById(taskId);
        return insightService.findForTask(taskId);
    }

    /** Теги локальной копии для выбора периода отчёта. */
    @GetMapping("/tags")
    public List<GitTag> tags(@PathVariable Integer taskId) {
        AnalysisTask task = taskService.getById(taskId);
        try {
            return gitService.listTags(task.getRepositoryUrl(), task.getBranch());
        } catch (RepositoryNotFoundException exception) {
            return List.of();
        }
    }
}
