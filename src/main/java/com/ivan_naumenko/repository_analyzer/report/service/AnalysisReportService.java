package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.report.dto.AnalysisReportResponse;
import com.ivan_naumenko.repository_analyzer.report.exception.ReportNotFoundException;
import com.ivan_naumenko.repository_analyzer.report.exception.ReportNotReadyException;
import com.ivan_naumenko.repository_analyzer.report.model.AnalysisReport;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.repository.AnalysisReportRepository;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisReportService {

    private static final int MAX_ERROR_LENGTH = 2_000;

    private final AnalysisReportRepository repository;
    private final AnalysisTaskService taskService;
    private final SqliteWriteCoordinator writeCoordinator;

    public AnalysisReport createOrGet(
            Integer taskId,
            ReportAudience audience
    ) {
        Objects.requireNonNull(audience, "audience не должен быть null");
        AnalysisTask task = taskService.getById(taskId);
        if (task.getStatus() != AnalysisTaskStatus.COMPLETED) {
            throw new ReportNotReadyException(
                    "Технический анализ задачи "
                            + taskId
                            + " ещё не завершён"
            );
        }

        return writeCoordinator.execute(
                () -> createOrGetUnderWriteLock(task, audience)
        );
    }

    private AnalysisReport createOrGetUnderWriteLock(
            AnalysisTask task,
            ReportAudience audience
    ) {
        Optional<AnalysisReport> existing = repository
                .findByAnalysisTask_IdAndAudience(task.getId(), audience);
        if (existing.isPresent()) {
            return existing.get();
        }
        LocalDateTime now = LocalDateTime.now();
        AnalysisReport report = new AnalysisReport();
        report.setAnalysisTask(task);
        report.setAudience(audience);
        report.setStatus(ReportStatus.NEW);
        report.setCreatedAt(now);
        report.setUpdatedAt(now);

        try {
            return repository.saveAndFlush(report);
        } catch (DataIntegrityViolationException exception) {
            return repository.findByAnalysisTask_IdAndAudience(
                            task.getId(),
                            audience
                    )
                    .orElseThrow(() -> exception);
        }
    }

    public boolean claim(Integer reportId) {
        return updateStatus(
                reportId,
                ReportStatus.NEW,
                ReportStatus.PROCESSING
        );
    }

    public boolean resetFailed(Integer reportId) {
        return updateStatus(
                reportId,
                ReportStatus.FAILED,
                ReportStatus.NEW
        );
    }

    public boolean returnToNew(Integer reportId) {
        return updateStatus(
                reportId,
                ReportStatus.PROCESSING,
                ReportStatus.NEW
        );
    }

    public ReportGenerationData getGenerationData(Integer reportId) {
        return repository.findGenerationData(
                        reportId,
                        ReportStatus.PROCESSING
                )
                .orElseThrow(() -> new ReportNotFoundException(
                        "Отчёт не найден в статусе PROCESSING: " + reportId
                ));
    }

    public void initializeProgress(Integer reportId, int totalCommits) {
        int updated = writeCoordinator.execute(() -> repository.initializeProgress(
                reportId,
                ReportStatus.PROCESSING,
                totalCommits,
                LocalDateTime.now()
        ));
        if (updated != 1) {
            throw new IllegalStateException(
                    "Не удалось инициализировать отчёт " + reportId
            );
        }
    }

    public void updateProgress(
            Integer reportId,
            int processedCommits,
            String content
    ) {
        int updated = writeCoordinator.execute(() -> repository.updateProgress(
                reportId,
                ReportStatus.PROCESSING,
                processedCommits,
                content,
                LocalDateTime.now()
        ));
        if (updated != 1) {
            throw new IllegalStateException(
                    "Не удалось обновить прогресс отчёта " + reportId
            );
        }
    }

    public void complete(Integer reportId, String content) {
        int updated = writeCoordinator.execute(() -> repository.complete(
                reportId,
                ReportStatus.PROCESSING,
                ReportStatus.COMPLETED,
                content,
                LocalDateTime.now()
        ));
        if (updated != 1) {
            throw new IllegalStateException(
                    "Не удалось завершить отчёт " + reportId
            );
        }
    }

    public boolean fail(Integer reportId, Throwable cause) {
        String message = cause.getClass().getSimpleName()
                + ": "
                + Optional.ofNullable(cause.getMessage())
                .orElse("без сообщения");
        if (message.length() > MAX_ERROR_LENGTH) {
            message = message.substring(0, MAX_ERROR_LENGTH);
        }

        String lastError = message;
        return writeCoordinator.execute(() -> repository.fail(
                reportId,
                ReportStatus.PROCESSING,
                ReportStatus.FAILED,
                lastError,
                LocalDateTime.now()
        ) == 1);
    }

    @Transactional(readOnly = true)
    public AnalysisReportResponse getResponse(Integer reportId) {
        AnalysisReport report = repository.findById(reportId)
                .orElseThrow(() -> new ReportNotFoundException(
                        "Отчёт не найден: " + reportId
                ));

        return toResponse(report);
    }

    @Transactional(readOnly = true)
    public List<AnalysisReportResponse> getAllForTask(Integer taskId) {
        taskService.getById(taskId);
        return repository.findAllByAnalysisTask_IdOrderByCreatedAtAsc(taskId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AnalysisReportResponse toResponse(AnalysisReport report) {
        return new AnalysisReportResponse(
                report.getId(),
                report.getAnalysisTask().getId(),
                report.getAudience(),
                report.getStatus(),
                report.getTotalCommits(),
                report.getProcessedCommits(),
                report.getContent(),
                report.getLastError(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }

    private boolean updateStatus(
            Integer reportId,
            ReportStatus expected,
            ReportStatus next
    ) {
        return writeCoordinator.execute(() -> repository.updateStatusIfCurrent(
                reportId,
                expected,
                next,
                LocalDateTime.now()
        ) == 1);
    }
}
