package com.ivan_naumenko.repository_analyzer.report.controller;

import com.ivan_naumenko.repository_analyzer.report.dto.AnalysisReportResponse;
import com.ivan_naumenko.repository_analyzer.report.dto.CreateReportRequest;
import com.ivan_naumenko.repository_analyzer.report.model.AnalysisReport;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.orchestrator.ReportOrchestrator;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
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
                request.audience()
        );
        boolean started = orchestrator.start(report.getId());
        AnalysisReportResponse response = reportService.getResponse(
                report.getId()
        );

        return started
                ? ResponseEntity.accepted().body(response)
                : ResponseEntity.ok(response);
    }

    @GetMapping("/reports/{reportId}")
    public AnalysisReportResponse get(@PathVariable Integer reportId) {
        return reportService.getResponse(reportId);
    }

    @PostMapping("/reports/{reportId}/retry")
    public ResponseEntity<AnalysisReportResponse> retry(
            @PathVariable Integer reportId
    ) {
        if (!reportService.resetFailed(reportId)
                || !orchestrator.start(reportId)) {
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
                || current.status() == ReportStatus.FAILED;
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
