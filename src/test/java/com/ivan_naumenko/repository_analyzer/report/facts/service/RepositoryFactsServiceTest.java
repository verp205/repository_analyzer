package com.ivan_naumenko.repository_analyzer.report.facts.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.commit.repository.RepositoryCommitRepository;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightService;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.diff.service.TaskChangeService;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse.AuthorFact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryFactsServiceTest {

    @Mock
    private AnalysisTaskService taskService;

    @Mock
    private RepositoryCommitRepository commitRepository;

    @Mock
    private TaskChangeService changeService;

    @Mock
    private CommitInsightService insightService;

    @InjectMocks
    private RepositoryFactsService service;

    @Test
    void mergesAuthorAliasesSharingEmail() {
        AnalysisTask task = new AnalysisTask();
        task.setRepositoryUrl("https://github.com/acme/smartstock.git");
        task.setBranch("main");
        when(taskService.getById(1)).thenReturn(task);
        when(insightService.findDrafts(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.anyList())).thenReturn(Map.of());

        RepositoryFactsResponse facts = service.build(1, List.of(
                commit(1, "verp205", "ivan@example.com"),
                commit(2, "Ivan Naumenko", "IVAN@example.com"),
                commit(3, "verp205", "ivan@example.com"),
                commit(4, "Anna", "anna@example.com")
        ));

        assertEquals(2, facts.authorCount());
        assertEquals(List.of(
                new AuthorFact("verp205", 3, 30, 3, 75.0),
                new AuthorFact("Anna", 1, 10, 1, 25.0)
        ), facts.authors());
    }

    private CommitChangeSummary commit(int id, String name, String email) {
        return new CommitChangeSummary(
                id,
                id,
                "%040d".formatted(id),
                "commit " + id,
                name,
                email,
                Instant.parse("2026-04-17T10:00:00Z").plusSeconds(id * 3_600L),
                10,
                1,
                List.of(),
                1
        );
    }
}
