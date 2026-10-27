package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.diff.service.TaskChangeService;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import com.ivan_naumenko.repository_analyzer.report.exception.ReportNotReadyException;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriod;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriodType;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.repository.AnalysisReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisReportServiceTest {

    @Mock
    private AnalysisReportRepository repository;

    @Mock
    private AnalysisTaskService taskService;

    @Mock
    private TaskChangeService changeService;

    @Mock
    private ReportPeriodResolver periodResolver;

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

    @Test
    void rejectsPeriodWithoutCommits() {
        AnalysisTask task = new AnalysisTask();
        task.setId(3);
        task.setRepositoryUrl("https://example.com/project.git");
        task.setBranch("main");
        task.setStatus(AnalysisTaskStatus.COMPLETED);
        when(taskService.getById(3)).thenReturn(task);
        when(changeService.load(3)).thenReturn(List.of());
        when(periodResolver.filter(any(), anyList())).thenReturn(List.of());

        ReportNotReadyException exception = assertThrows(
                ReportNotReadyException.class,
                () -> service().createOrGet(
                        3,
                        ReportAudience.DIRECTOR,
                        new ReportPeriod(ReportPeriodType.SINCE_TAG, null, "v2.0", null)
                )
        );

        assertTrue(exception.getMessage().contains("после тега v2.0"));
        verify(repository, never()).saveAndFlush(any());
    }

    private AnalysisReportService service() {
        return new AnalysisReportService(
                repository,
                taskService,
                new SqliteWriteCoordinator(),
                changeService,
                periodResolver,
                org.mockito.Mockito.mock(com.ivan_naumenko.repository_analyzer.report.snapshot.RepositorySnapshotService.class),
                org.mockito.Mockito.mock(com.ivan_naumenko.repository_analyzer.report.facts.service.RepositoryFactsService.class),
                new tools.jackson.databind.ObjectMapper()
        );
    }
}
