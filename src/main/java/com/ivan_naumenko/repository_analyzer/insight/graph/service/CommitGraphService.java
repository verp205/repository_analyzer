package com.ivan_naumenko.repository_analyzer.insight.graph.service;

import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitParent;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import com.ivan_naumenko.repository_analyzer.commit.repository.CommitParentRepository;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.insight.graph.dto.CommitGraphEdge;
import com.ivan_naumenko.repository_analyzer.insight.graph.dto.CommitGraphNode;
import com.ivan_naumenko.repository_analyzer.insight.graph.dto.CommitGraphResponse;
import lombok.RequiredArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommitGraphService {

    private final AnalysisTaskService taskService;
    private final RepositoryCommitRepository commitRepository;
    private final CommitParentRepository parentRepository;

    @Transactional(readOnly = true)
    public CommitGraphResponse build(Integer taskId) {
        taskService.getById(taskId);

        List<RepositoryCommit> commits = commitRepository
                .findAllByAnalysisTask_Id(taskId);
        Map<String, RepositoryCommit> commitsByHash = new HashMap<>();
        Graph<String, DefaultEdge> graph = new DirectedAcyclicGraph<>(
                DefaultEdge.class
        );

        for (RepositoryCommit commit : commits) {
            commitsByHash.put(commit.getCommitHash(), commit);
            graph.addVertex(commit.getCommitHash());
        }

        List<CommitGraphEdge> edges = new ArrayList<>();
        int missingParents = 0;
        for (CommitParent relation
                : parentRepository.findAllByChildCommit_AnalysisTask_Id(taskId)) {
            String parentHash = relation.getParentHash();
            String childHash = relation.getChildCommit().getCommitHash();

            if (!commitsByHash.containsKey(parentHash)) {
                missingParents++;
                continue;
            }

            graph.addEdge(parentHash, childHash);
            edges.add(new CommitGraphEdge(
                    parentHash,
                    childHash,
                    relation.getParentPosition()
            ));
        }

        List<CommitGraphNode> nodes = commits.stream()
                .sorted(Comparator.comparing(RepositoryCommit::getCommittedAt)
                        .thenComparing(RepositoryCommit::getId))
                .map(this::toNode)
                .toList();
        edges.sort(Comparator.comparing(CommitGraphEdge::childHash)
                .thenComparingInt(CommitGraphEdge::parentPosition));

        int roots = (int) graph.vertexSet().stream()
                .filter(vertex -> graph.inDegreeOf(vertex) == 0)
                .count();
        int tips = (int) graph.vertexSet().stream()
                .filter(vertex -> graph.outDegreeOf(vertex) == 0)
                .count();
        int merges = (int) graph.vertexSet().stream()
                .filter(vertex -> graph.inDegreeOf(vertex) > 1)
                .count();

        return new CommitGraphResponse(
                taskId,
                nodes.size(),
                roots,
                tips,
                merges,
                maxDepth(graph),
                missingParents,
                List.copyOf(nodes),
                List.copyOf(edges)
        );
    }

    private CommitGraphNode toNode(RepositoryCommit commit) {
        return new CommitGraphNode(
                commit.getId(),
                commit.getCommitHash(),
                commit.getMessage(),
                commit.getAuthorName(),
                commit.getCommittedAt(),
                commit.getProcessingStatus()
        );
    }

    private int maxDepth(Graph<String, DefaultEdge> graph) {
        Map<String, Integer> depths = new HashMap<>();
        TopologicalOrderIterator<String, DefaultEdge> iterator =
                new TopologicalOrderIterator<>(graph);
        int maxDepth = 0;

        while (iterator.hasNext()) {
            String vertex = iterator.next();
            int depth = graph.incomingEdgesOf(vertex).stream()
                    .map(graph::getEdgeSource)
                    .mapToInt(parent -> depths.get(parent) + 1)
                    .max()
                    .orElse(0);
            depths.put(vertex, depth);
            maxDepth = Math.max(maxDepth, depth);
        }

        return maxDepth;
    }
}
