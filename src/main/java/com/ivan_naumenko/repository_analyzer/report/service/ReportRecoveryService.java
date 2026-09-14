package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.repository.AnalysisReportRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(
            ReportRecoveryService.class
    );

    private final AnalysisReportRepository repository;
    private final AnalysisReportService reportService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedReports() {
        repository.findIdsByStatus(ReportStatus.PROCESSING)
                .forEach(reportId -> {
                    try {
                        reportService.returnToNew(reportId);
                    } catch (RuntimeException exception) {
                        log.error(
                                "Не удалось подготовить отчёт {} к восстановлению",
                                reportId,
                                exception
                        );
                    }
                });
    }
}
