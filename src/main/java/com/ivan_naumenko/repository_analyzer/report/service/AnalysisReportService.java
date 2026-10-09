package com.ivan_naumenko.repository_analyzer.report.service;

import com.ivan_naumenko.repository_analyzer.report.snapshot.RepositorySnapshotService;
import com.ivan_naumenko.repository_analyzer.report.snapshot.RepositorySnapshot;
import com.ivan_naumenko.repository_analyzer.report.facts.service.RepositoryFactsService;
import com.ivan_naumenko.repository_analyzer.report.facts.dto.RepositoryFactsResponse;
import com.ivan_naumenko.repository_analyzer.report.artifact.ReportArtifacts;
import com.ivan_naumenko.repository_analyzer.diff.model.CommitChangeSummary;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.analysis.service.AnalysisTaskService;
import com.ivan_naumenko.repository_analyzer.diff.service.TaskChangeService;
import com.ivan_naumenko.repository_analyzer.persistence.SqliteWriteCoordinator;
import com.ivan_naumenko.repository_analyzer.report.dto.AnalysisReportResponse;
import com.ivan_naumenko.repository_analyzer.report.dto.ReportPeriodResponse;
import com.ivan_naumenko.repository_analyzer.report.exception.ReportNotFoundException;
import com.ivan_naumenko.repository_analyzer.report.exception.ReportNotReadyException;
import com.ivan_naumenko.repository_analyzer.report.model.AnalysisReport;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriod;
import com.ivan_naumenko.repository_analyzer.report.model.ReportPeriodType;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import com.ivan_naumenko.repository_analyzer.report.repository.AnalysisReportRepository;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalysisReportService {

    private static final int MAX_ERROR_LENGTH = 2_000;
    private static final List<ReportStatus> RETRYABLE_STATUSES = List.of(
            ReportStatus.FAILED,
            ReportStatus.CANCELLED,
            ReportStatus.COMPLETED
    );

    private final AnalysisReportRepository repository;
    private final AnalysisTaskService taskService;
    private final SqliteWriteCoordinator writeCoordinator;
    private final TaskChangeService changeService;
    private final ReportPeriodResolver periodResolver;
    private final RepositorySnapshotService snapshotService;
    private final RepositoryFactsService factsService;
    private final ObjectMapper mapper;

    public AnalysisReport createOrGet(
            Integer taskId,
            ReportAudience audience
    ) {
        return createOrGet(taskId, audience, ReportPeriod.all());
    }

    public AnalysisReport createOrGet(
            Integer taskId,
            ReportAudience audience,
            ReportPeriod period
    ) {
        Objects.requireNonNull(audience, "audience не должен быть null");
        ReportPeriod resolvedPeriod = period == null ? ReportPeriod.all() : period;
        return writeCoordinator.execute(() -> {
            AnalysisTask task = taskService.getById(taskId);
            if (task.getStatus() != AnalysisTaskStatus.COMPLETED) {
                throw new ReportNotReadyException("Технический анализ задачи ещё не завершён");
            }
            if (resolvedPeriod.type() != ReportPeriodType.ALL) ensurePeriodHasCommits(task, audience, resolvedPeriod);
            return createOrGetUnderWriteLock(task, audience, resolvedPeriod);
        });
    }

    private void ensurePeriodHasCommits(
            AnalysisTask task,
            ReportAudience audience,
            ReportPeriod period
    ) {
        ReportGenerationData scope = new ReportGenerationData(
                null,
                task.getId(),
                audience,
                task.getRepositoryUrl(),
                task.getBranch(),
                period
        );
        if (periodResolver.filter(scope, changeService.load(task.getId())).isEmpty()) {
            throw new ReportNotReadyException(
                    "В выбранном периоде («" + period.label() + "») нет проанализированных коммитов"
            );
        }
    }

    private AnalysisReport createOrGetUnderWriteLock(
            AnalysisTask task,
            ReportAudience audience,
            ReportPeriod period
    ) {
        Optional<AnalysisReport> existing = repository
                .findByAnalysisTask_IdAndAudienceAndPeriodKeyAndAnalysisRevision(
                        task.getId(),
                        audience,
                        period.key(), task.getRevision()
                );
        if (existing.isPresent()) {
            return existing.get();
        }
        LocalDateTime now = LocalDateTime.now();
        AnalysisReport report = new AnalysisReport();
        report.setAnalysisTask(task);
        report.setAudience(audience);
        report.setPeriod(period);
        report.setStatus(ReportStatus.NEW);
        report.setAnalysisRevision(task.getRevision());
        report.setHeadCommitHash(task.getHeadCommitHash());
        pinInputs(report);
        report.setCreatedAt(now);
        report.setUpdatedAt(now);

        try {
            return repository.saveAndFlush(report);
        } catch (DataIntegrityViolationException exception) {
            return repository.findByAnalysisTask_IdAndAudienceAndPeriodKeyAndAnalysisRevision(
                            task.getId(),
                            audience,
                            period.key(), task.getRevision()
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

    public boolean resetForRetry(Integer reportId) {
        return writeCoordinator.execute(() -> repository.resetForRetry(
                reportId,
                RETRYABLE_STATUSES,
                ReportStatus.NEW,
                LocalDateTime.now()
        ) == 1);
    }

    public boolean cancel(Integer reportId) {
        return updateStatus(
                reportId,
                ReportStatus.PROCESSING,
                ReportStatus.CANCELLED
        );
    }

    @Transactional(readOnly = true)
    public boolean isCancelled(Integer reportId) {
        return repository.existsByIdAndStatus(
                reportId,
                ReportStatus.CANCELLED
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

    /** Задача, роль и период отчёта в любом статусе. */
    @Transactional(readOnly = true)
    public ReportGenerationData getScope(Integer reportId) {
        return repository.findGenerationDataById(reportId)
                .orElseThrow(() -> new ReportNotFoundException(
                        "Отчёт не найден: " + reportId
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

    public void updateProgress(Integer reportId, int processedCommits) {
        int updated = writeCoordinator.execute(() -> repository.updateProgress(
                reportId,
                ReportStatus.PROCESSING,
                processedCommits,
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
                null, null,
                LocalDateTime.now()
        ));
        if (updated != 1) {
            throw new IllegalStateException(
                    "Не удалось завершить отчёт " + reportId
            );
        }
    }

    public void complete(Integer reportId,
                         ReportArtifacts.SynthesisResult result,
                         RepositoryFactsResponse facts) {
        int updated = writeCoordinator.execute(() -> repository.complete(reportId,
                ReportStatus.PROCESSING, ReportStatus.COMPLETED, result.content(),
                mapper.writeValueAsString(result.artifacts()), mapper.writeValueAsString(
                        com.ivan_naumenko.repository_analyzer.report.grounding.ReviewedFacts.apply(facts, result.artifacts().coverage())), LocalDateTime.now()));
        if (updated != 1) throw new IllegalStateException("Не удалось завершить отчёт " + reportId);
    }

    /** Freeze legacy inputs before a branch can be updated. New reports are pinned on creation. */
    public void freezeInputsForTask(Integer taskId) {
        writeCoordinator.execute(() -> {
            for (AnalysisReport report : repository.findAllByAnalysisTask_IdOrderByCreatedAtAsc(taskId)) {
                if (report.getCommitIdsJson() == null) {
                    pinInputs(report);
                    repository.saveAndFlush(report);
                }
            }
        });
    }

    private void pinInputs(AnalysisReport report) {
        AnalysisTask task = taskService.getById(report.getAnalysisTask().getId());
        ReportGenerationData scope = new ReportGenerationData(report.getId(), task.getId(), report.getAudience(),
                task.getRepositoryUrl(), task.getBranch(), report.getPeriod());
        List<CommitChangeSummary> commits =
                periodResolver.filter(scope, changeService.load(task.getId()));
        report.setCommitIdsJson(mapper.writeValueAsString(commits.stream().map(c -> c.commitId()).toList()));
        if (report.getHeadCommitHash() == null) report.setHeadCommitHash(snapshotService.headCommitHash(task.getRepositoryUrl(), task.getBranch()));
        report.setSnapshotJson(mapper.writeValueAsString(snapshotService.capture(
                task.getRepositoryUrl(), task.getBranch(), report.getHeadCommitHash())));
        if (report.getFactsJson() == null) report.setFactsJson(mapper.writeValueAsString(factsService.build(task.getId(), commits)));
    }

    public List<CommitChangeSummary> loadCommits(Integer reportId) {
        AnalysisReport report = repository.findById(reportId).orElseThrow(() -> new ReportNotFoundException("Отчёт не найден"));
        if (report.getCommitIdsJson() == null) {
            freezeInputsForTask(report.getAnalysisTask().getId());
            report = repository.findById(reportId).orElseThrow();
        }
        int[] ids = mapper.readValue(report.getCommitIdsJson(), int[].class);
        return changeService.loadForCommits(report.getAnalysisTask().getId(), java.util.Arrays.stream(ids).boxed().toList());
    }

    @Transactional(readOnly = true)
    public RepositorySnapshot snapshot(Integer reportId) {
        AnalysisReport report = repository.findById(reportId).orElseThrow(() -> new ReportNotFoundException("Отчёт не найден"));
        return report.getSnapshotJson() == null
                ? RepositorySnapshot.unavailable()
                : mapper.readValue(report.getSnapshotJson(), RepositorySnapshot.class);
    }

    @Transactional(readOnly = true)
    public RepositoryFactsResponse storedFacts(Integer reportId) {
        AnalysisReport report = repository.findById(reportId).orElseThrow(() -> new ReportNotFoundException("Отчёт не найден"));
        return report.getStatus() != ReportStatus.COMPLETED || report.getFactsJson() == null ? null : mapper.readValue(report.getFactsJson(),
                RepositoryFactsResponse.class);
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

    @Transactional(readOnly = true)
    public List<com.ivan_naumenko.repository_analyzer.report.dto.ReportSummaryResponse> getSummaries(Integer taskId) {
        taskService.getById(taskId);
        return repository.findSummaries(taskId).stream().map(item ->
                new com.ivan_naumenko.repository_analyzer.report.dto.ReportSummaryResponse(item.getId(), item.getAudience(),
                        item.getStatus(), item.getAnalysisRevision(), item.getHeadCommitHash(), ReportPeriodResponse.from(
                        new ReportPeriod(item.getPeriodType(), item.getPeriodDays(), item.getPeriodFrom(), item.getPeriodTo())),
                        item.getCreatedAt())).toList();
    }

    private AnalysisReportResponse toResponse(AnalysisReport report) {
        return new AnalysisReportResponse(
                report.getId(),
                report.getAnalysisTask().getId(),
                report.getAnalysisTask().getRepositoryUrl(),
                report.getAnalysisTask().getBranch(),
                ReportPeriodResponse.from(report.getPeriod()),
                report.getAudience(),
                report.getStatus(),
                report.getTotalCommits(),
                report.getProcessedCommits(),
                report.getContent(),
                report.getLastError(),
                report.getCreatedAt(),
                report.getUpdatedAt(),
                report.getAnalysisRevision(), report.getHeadCommitHash(),
                report.getArtifactsJson() == null ? null : mapper.readValue(report.getArtifactsJson(),
                        ReportArtifacts.class)
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
