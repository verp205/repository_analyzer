package com.ivan_naumenko.repository_analyzer.report.controller;

import com.ivan_naumenko.repository_analyzer.analysis.exception.GlobalExceptionHandler;
import com.ivan_naumenko.repository_analyzer.report.dto.AnalysisReportResponse;
import com.ivan_naumenko.repository_analyzer.report.dto.ReportPeriodResponse;
import com.ivan_naumenko.repository_analyzer.report.model.AnalysisReport;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriod;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriodType;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.orchestrator.ReportOrchestrator;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
import com.ivan_naumenko.repository_analyzer.report.service.ReportScopeService;
import com.ivan_naumenko.repository_analyzer.report.sse.ReportEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnalysisReportControllerTest {

    private static final int REPORT_ID = 17;
    private static final int TASK_ID = 5;

    @Mock
    private AnalysisReportService reportService;

    @Mock
    private ReportOrchestrator orchestrator;

    @Mock
    private ReportEventService eventService;

    @Mock
    private ReportScopeService scopeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AnalysisReportController(
                                reportService,
                                orchestrator,
                                eventService,
                                scopeService
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsReportForSelectedPeriod() throws Exception {
        ReportPeriod period = new ReportPeriod(ReportPeriodType.LAST_DAYS, 90, null, null);
        AnalysisReport report = new AnalysisReport();
        report.setId(REPORT_ID);
        when(reportService.createOrGet(TASK_ID, ReportAudience.ANALYST, period))
                .thenReturn(report);
        when(orchestrator.start(REPORT_ID)).thenReturn(true);
        when(reportService.getResponse(REPORT_ID))
                .thenReturn(response(ReportStatus.PROCESSING, period));

        mockMvc.perform(post("/api/analysis-tasks/{taskId}/reports", TASK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"audience":"ANALYST","period":{"type":"LAST_DAYS","days":90}}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.period.type").value("LAST_DAYS"))
                .andExpect(jsonPath("$.period.label").value("последние 3 месяца истории"));
    }

    @Test
    void rejectsInvalidPeriod() throws Exception {
        mockMvc.perform(post("/api/analysis-tasks/{taskId}/reports", TASK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"audience":"ANALYST","period":{"type":"BETWEEN_TAGS","fromTag":"v1","toTag":"v1"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Начальный и конечный теги должны различаться"));

        verify(reportService, never()).createOrGet(any(), any(), any());
    }

    @Test
    void cancelsProcessingReport() throws Exception {
        when(orchestrator.cancel(REPORT_ID)).thenReturn(true);
        when(reportService.getResponse(REPORT_ID))
                .thenReturn(response(ReportStatus.CANCELLED, ReportPeriod.all()));

        mockMvc.perform(post("/api/reports/{reportId}/cancel", REPORT_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(REPORT_ID))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(orchestrator).cancel(REPORT_ID);
    }

    @Test
    void returnsConflictWhenReportIsAlreadyTerminal() throws Exception {
        when(orchestrator.cancel(REPORT_ID)).thenReturn(false);
        when(reportService.getResponse(REPORT_ID))
                .thenReturn(response(ReportStatus.COMPLETED, ReportPeriod.all()));

        mockMvc.perform(post("/api/reports/{reportId}/cancel", REPORT_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private AnalysisReportResponse response(ReportStatus status, ReportPeriod period) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 15, 0);
        return new AnalysisReportResponse(
                REPORT_ID,
                TASK_ID,
                "https://example.com/project.git",
                "main",
                ReportPeriodResponse.from(period),
                ReportAudience.DEVELOPER,
                status,
                10,
                3,
                "partial content",
                null,
                now,
                now
        );
    }
}
