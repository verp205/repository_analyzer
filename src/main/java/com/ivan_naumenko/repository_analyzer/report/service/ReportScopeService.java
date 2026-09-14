package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.commitinsight.dto.CommitInsightResponse;
import com.ivan_naumenko.repository_analyzer.commitinsight.service.CommitInsightService;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.diff.service.TaskChangeService;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.facts.service.RepositoryFactsService;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriodType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Показатели и разбор коммитов в границах периода конкретного отчёта. */
@Service
@RequiredArgsConstructor
public class ReportScopeService {

    private final AnalysisReportService reportService;
    private final TaskChangeService changeService;
    private final ReportPeriodResolver periodResolver;
    private final RepositoryFactsService factsService;
    private final CommitInsightService insightService;

    public RepositoryFactsResponse facts(Integer reportId) {
        ReportGenerationData scope = reportService.getScope(reportId);
        return scope.period().type() == ReportPeriodType.ALL
                ? factsService.build(scope.taskId())
                : factsService.build(scope.taskId(), commits(scope));
    }

    public List<CommitInsightResponse> commitInsights(Integer reportId) {
        ReportGenerationData scope = reportService.getScope(reportId);
        return insightService.findForCommits(scope.taskId(), commits(scope));
    }

    private List<CommitChangeSummary> commits(ReportGenerationData scope) {
        return periodResolver.filter(scope, changeService.load(scope.taskId()));
    }
}
