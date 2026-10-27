package com.ivan_naumenko.repository_analyzer.analysis.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.orchestrator.AnalysisOrchestrator;
import com.ivan_naumenko.repository_analyzer.git.model.GitCredentials;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import com.ivan_naumenko.repository_analyzer.report.exception.ReportNotReadyException;
import com.ivan_naumenko.repository_analyzer.report.service.AnalysisReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalysisRefreshService {
    private final AnalysisTaskService taskService;
    private final AnalysisReportService reportService;
    private final AnalysisOrchestrator orchestrator;
    private final SqliteWriteCoordinator writes;

    public boolean refresh(Integer taskId, GitCredentials credentials) {
        boolean[] handedOver = {false};
        try {
            return writes.execute(() -> {
                if (taskService.getById(taskId).getStatus() != AnalysisTaskStatus.COMPLETED) {
                    throw new ReportNotReadyException("Обновление доступно после завершения анализа");
                }
                reportService.freezeInputsForTask(taskId);
                if (!taskService.updateStatusIfCurrent(taskId, AnalysisTaskStatus.COMPLETED, AnalysisTaskStatus.NEW)) {
                    return false;
                }
                handedOver[0] = true;
                return orchestrator.start(taskId, credentials);
            });
        } finally {
            if (!handedOver[0] && credentials != null) credentials.close();
        }
    }
}
