package com.ivan_naumenko.repository_analyzer.report.controller;

import com.ivan_naumenko.repository_analyzer.commitinsight.dto.CommitInsightResponse;
import com.ivan_naumenko.repository_analyzer.report.dto.AnalysisReportResponse;
import com.ivan_naumenko.repository_analyzer.report.dto.CreateReportRequest;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.model.AnalysisReport;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.orchestrator.ReportOrchestrator;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
import com.ivan_naumenko.repository_analyzer.report.service.ReportScopeService;
import com.ivan_naumenko.repository_analyzer.report.sse.ReportEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AnalysisReportController {

    private final AnalysisReportService reportService;
    private final ReportOrchestrator orchestrator;
    private final ReportEventService eventService;
    private final ReportScopeService scopeService;
    private final com.ivan_naumenko.repository_analyzer.report.artifact.ReportEvidenceService evidenceService;

    @GetMapping("/analysis-tasks/{taskId}/reports")
    public List<AnalysisReportResponse> getForTask(
            @PathVariable Integer taskId
    ) {
        return reportService.getAllForTask(taskId);
    }

    @PostMapping("/analysis-tasks/{taskId}/reports")
    public ResponseEntity<AnalysisReportResponse> createOrGet(
            @PathVariable Integer taskId,
            @Valid @RequestBody CreateReportRequest request
    ) {
        AnalysisReport report = reportService.createOrGet(
                taskId,
                request.audience(),
                request.toPeriod()
        );
        boolean started = orchestrator.start(report.getId());
        AnalysisReportResponse response = reportService.getResponse(
                report.getId()
        );

        return started
                ? ResponseEntity.accepted().body(response)
                : ResponseEntity.ok(response);
    }

    @GetMapping("/analysis-tasks/{taskId}/report-summaries")
    public List<com.ivan_naumenko.repository_analyzer.report.dto.ReportSummaryResponse> summaries(@PathVariable Integer taskId) {
        return reportService.getSummaries(taskId);
    }

    @GetMapping("/reports/{reportId}")
    public AnalysisReportResponse get(@PathVariable Integer reportId) {
        return reportService.getResponse(reportId);
    }

    @GetMapping("/reports/{reportId}/facts")
    public RepositoryFactsResponse facts(@PathVariable Integer reportId) {
        return scopeService.facts(reportId);
    }

    @GetMapping("/reports/{reportId}/commit-insights")
    public List<CommitInsightResponse> commitInsights(
            @PathVariable Integer reportId
    ) {
        return scopeService.commitInsights(reportId);
    }

    @GetMapping("/reports/{reportId}/evidence/{evidenceId}")
    public com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.EvidenceDetail evidence(
            @PathVariable Integer reportId, @PathVariable String evidenceId) {
        return evidenceService.detail(reportId, evidenceId);
    }

    @GetMapping("/reports/{reportId}/evidence/{evidenceId}/files/{position}")
    public com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts.EvidenceFile evidenceFile(
            @PathVariable Integer reportId, @PathVariable String evidenceId, @PathVariable int position) {
        return evidenceService.file(reportId, evidenceId, position);
    }

    @PostMapping("/reports/{reportId}/retry")
    public ResponseEntity<AnalysisReportResponse> retry(
            @PathVariable Integer reportId
    ) {
        if (!reportService.resetForRetry(reportId)
                || !orchestrator.start(reportId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(reportService.getResponse(reportId));
        }

        return ResponseEntity.accepted()
                .body(reportService.getResponse(reportId));
    }

    @PostMapping("/reports/{reportId}/cancel")
    public ResponseEntity<AnalysisReportResponse> cancel(
            @PathVariable Integer reportId
    ) {
        if (!orchestrator.cancel(reportId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(reportService.getResponse(reportId));
        }

        return ResponseEntity.accepted()
                .body(reportService.getResponse(reportId));
    }

    @GetMapping(
            value = "/reports/{reportId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter events(@PathVariable Integer reportId) {
        reportService.getResponse(reportId);
        SseEmitter emitter = eventService.subscribe(reportId);
        AnalysisReportResponse current = reportService.getResponse(reportId);
        boolean terminal = current.status() == ReportStatus.COMPLETED
                || current.status() == ReportStatus.FAILED
                || current.status() == ReportStatus.CANCELLED;
        eventService.sendSnapshot(
                reportId,
                emitter,
                current.status().name(),
                current.processedCommits(),
                current.totalCommits(),
                terminal
        );
        return emitter;
    }
}
