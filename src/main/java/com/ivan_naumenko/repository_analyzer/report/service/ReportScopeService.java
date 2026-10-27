package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.commitinsight.dto.CommitInsightResponse;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightService;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.facts.service.RepositoryFactsService;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Показатели и разбор коммитов в границах периода конкретного отчёта. */
@Service
@RequiredArgsConstructor
public class ReportScopeService {

    private final AnalysisReportService reportService;
    private final RepositoryFactsService factsService;
    private final CommitInsightService insightService;

    public RepositoryFactsResponse facts(Integer reportId) {
        ReportGenerationData scope = reportService.getScope(reportId);
        RepositoryFactsResponse stored = reportService.storedFacts(reportId);
        return stored == null ? factsService.build(scope.taskId(), commits(scope)) : stored;
    }

    public List<CommitInsightResponse> commitInsights(Integer reportId) {
        ReportGenerationData scope = reportService.getScope(reportId);
        return insightService.findForCommits(scope.taskId(), commits(scope));
    }

    private List<CommitChangeSummary> commits(ReportGenerationData scope) {
        return reportService.loadCommits(scope.reportId());
    }
}
