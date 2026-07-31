package com.ivan_naumenko.repository_analyzer.report.controller;

import com.ivan_naumenko.repository_analyzer.report.dto.AnalysisReportResponse;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.orchestrator.ReportOrchestrator;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
import com.ivan_naumenko.repository_analyzer.report.sse.ReportEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnalysisReportControllerTest {

    private static final int REPORT_ID = 17;

    @Mock
    private AnalysisReportService reportService;

    @Mock
    private ReportOrchestrator orchestrator;

    @Mock
    private ReportEventService eventService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AnalysisReportController(
                        reportService,
                        orchestrator,
                        eventService
                )
        ).build();
    }

    @Test
    void cancelsProcessingReport() throws Exception {
        when(orchestrator.cancel(REPORT_ID)).thenReturn(true);
        when(reportService.getResponse(REPORT_ID))
                .thenReturn(response(ReportStatus.CANCELLED));

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
                .thenReturn(response(ReportStatus.COMPLETED));

        mockMvc.perform(post("/api/reports/{reportId}/cancel", REPORT_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private AnalysisReportResponse response(ReportStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 15, 0);
        return new AnalysisReportResponse(
                REPORT_ID,
                5,
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
