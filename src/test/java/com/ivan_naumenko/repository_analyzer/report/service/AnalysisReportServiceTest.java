package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.repository.AnalysisReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisReportServiceTest {

    @Mock
    private AnalysisReportRepository repository;

    @Mock
    private AnalysisTaskService taskService;

    @Test
    void cancelsOnlyProcessingReport() {
        int reportId = 10;
        when(repository.updateStatusIfCurrent(
                eq(reportId),
                eq(ReportStatus.PROCESSING),
                eq(ReportStatus.CANCELLED),
                any(LocalDateTime.class)
        )).thenReturn(1);

        assertTrue(service().cancel(reportId));

        verify(repository).updateStatusIfCurrent(
                eq(reportId),
                eq(ReportStatus.PROCESSING),
                eq(ReportStatus.CANCELLED),
                any(LocalDateTime.class)
        );
    }

    @Test
    void resetsFailedOrCancelledReportBeforeRetry() {
        int reportId = 20;
        when(repository.resetForRetry(
                eq(reportId),
                any(Collection.class),
                eq(ReportStatus.NEW),
                any(LocalDateTime.class)
        )).thenReturn(1);

        assertTrue(service().resetForRetry(reportId));

        verify(repository).resetForRetry(
                eq(reportId),
                any(Collection.class),
                eq(ReportStatus.NEW),
                any(LocalDateTime.class)
        );
    }

    private AnalysisReportService service() {
        return new AnalysisReportService(
                repository,
                taskService,
                new SqliteWriteCoordinator()
        );
    }
}
