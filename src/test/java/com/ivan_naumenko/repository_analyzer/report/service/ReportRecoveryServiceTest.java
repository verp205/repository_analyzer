package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.repository.AnalysisReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportRecoveryServiceTest {

    @Mock
    private AnalysisReportRepository repository;

    @Mock
    private AnalysisReportService reportService;

    @Test
    void resetsInterruptedReportsWithoutStartingBackgroundGeneration() {
        when(repository.findIdsByStatus(ReportStatus.PROCESSING))
                .thenReturn(List.of(10, 20));

        ReportRecoveryService service = new ReportRecoveryService(
                repository,
                reportService
        );

        service.recoverInterruptedReports();

        verify(reportService).returnToNew(10);
        verify(reportService).returnToNew(20);
        verifyNoMoreInteractions(reportService);
    }
}
