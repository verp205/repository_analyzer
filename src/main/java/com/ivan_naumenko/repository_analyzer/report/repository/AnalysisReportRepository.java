package com.ivan_naumenko.repository_analyzer.report.repository;

import com.ivan_naumenko.repository_analyzer.report.model.AnalysisReport;
import com.ivan_naumenko.repository_analyzer.report.model.ReportAudience;
import com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData;
import com.ivan_naumenko.repository_analyzer.report.model.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

public interface AnalysisReportRepository
        extends JpaRepository<AnalysisReport, Integer> {

    Optional<AnalysisReport> findByAnalysisTask_IdAndAudienceAndPeriodKey(
            Integer taskId,
            ReportAudience audience,
            String periodKey
    );

    List<AnalysisReport> findAllByAnalysisTask_IdOrderByCreatedAtAsc(
            Integer taskId
    );

    @Query("""
            SELECT report.id
            FROM AnalysisReport report
            WHERE report.status = :status
            ORDER BY report.createdAt ASC, report.id ASC
            """)
    List<Integer> findIdsByStatus(
            @Param("status") ReportStatus status
    );

    @Query("""
            SELECT new com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData(
                report.id,
                report.analysisTask.id,
                report.audience,
                report.analysisTask.repositoryUrl,
                report.analysisTask.branch,
                report.periodType,
                report.periodDays,
                report.periodFrom,
                report.periodTo
            )
            FROM AnalysisReport report
            WHERE report.id = :reportId
              AND report.status = :status
            """)
    Optional<ReportGenerationData> findGenerationData(
            @Param("reportId") Integer reportId,
            @Param("status") ReportStatus status
    );

    @Query("""
            SELECT new com.ivan_naumenko.repository_analyzer.report.model.ReportGenerationData(
                report.id,
                report.analysisTask.id,
                report.audience,
                report.analysisTask.repositoryUrl,
                report.analysisTask.branch,
                report.periodType,
                report.periodDays,
                report.periodFrom,
                report.periodTo
            )
            FROM AnalysisReport report
            WHERE report.id = :reportId
            """)
    Optional<ReportGenerationData> findGenerationDataById(
            @Param("reportId") Integer reportId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE AnalysisReport report
            SET report.status = :newStatus,
                report.lastError = null,
                report.updatedAt = :updatedAt
            WHERE report.id = :reportId
              AND report.status = :expectedStatus
            """)
    int updateStatusIfCurrent(
            @Param("reportId") Integer reportId,
            @Param("expectedStatus") ReportStatus expectedStatus,
            @Param("newStatus") ReportStatus newStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE AnalysisReport report
            SET report.status = :newStatus,
                report.totalCommits = 0,
                report.processedCommits = 0,
                report.content = null,
                report.lastError = null,
                report.updatedAt = :updatedAt
            WHERE report.id = :reportId
              AND report.status IN :expectedStatuses
            """)
    int resetForRetry(
            @Param("reportId") Integer reportId,
            @Param("expectedStatuses")
            Collection<ReportStatus> expectedStatuses,
            @Param("newStatus") ReportStatus newStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    boolean existsByIdAndStatus(Integer reportId, ReportStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE AnalysisReport report
            SET report.totalCommits = :totalCommits,
                report.processedCommits = 0,
                report.content = null,
                report.updatedAt = :updatedAt
            WHERE report.id = :reportId
              AND report.status = :status
            """)
    int initializeProgress(
            @Param("reportId") Integer reportId,
            @Param("status") ReportStatus status,
            @Param("totalCommits") int totalCommits,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE AnalysisReport report
            SET report.processedCommits = :processedCommits,
                report.updatedAt = :updatedAt
            WHERE report.id = :reportId
              AND report.status = :status
              AND report.totalCommits >= :processedCommits
            """)
    int updateProgress(
            @Param("reportId") Integer reportId,
            @Param("status") ReportStatus status,
            @Param("processedCommits") int processedCommits,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE AnalysisReport report
            SET report.status = :completedStatus,
                report.processedCommits = report.totalCommits,
                report.content = :content,
                report.lastError = null,
                report.updatedAt = :updatedAt
            WHERE report.id = :reportId
              AND report.status = :processingStatus
            """)
    int complete(
            @Param("reportId") Integer reportId,
            @Param("processingStatus") ReportStatus processingStatus,
            @Param("completedStatus") ReportStatus completedStatus,
            @Param("content") String content,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE AnalysisReport report
            SET report.status = :failedStatus,
                report.lastError = :lastError,
                report.updatedAt = :updatedAt
            WHERE report.id = :reportId
              AND report.status = :processingStatus
            """)
    int fail(
            @Param("reportId") Integer reportId,
            @Param("processingStatus") ReportStatus processingStatus,
            @Param("failedStatus") ReportStatus failedStatus,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
