package com.ivan_naumenko.repository_analyzer.analysis.repository;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskRecoveryCandidate;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnalysisTaskRepository extends JpaRepository<AnalysisTask, Integer> {

    Optional<AnalysisTask> findByRepositoryUrlAndBranch(
            String repositoryUrl,
            String branch
    );

    List<AnalysisTask> findAllByOrderByUpdatedAtDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE AnalysisTask task
        SET task.status = :newStatus,
            task.updatedAt = :updatedAt
        WHERE task.id = :taskId
          AND task.status = :expectedStatus
        """)
    int updateStatusIfCurrent(
            @Param("taskId") Integer taskId,
            @Param("expectedStatus") AnalysisTaskStatus expectedStatus,
            @Param("newStatus") AnalysisTaskStatus newStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE AnalysisTask task
        SET task.status = :newStatus,
            task.updatedAt = :updatedAt
        WHERE task.id = :taskId
          AND task.status IN :expectedStatuses
        """)
    int updateStatusIfCurrentIn(
            @Param("taskId") Integer taskId,
            @Param("expectedStatuses")
            Collection<AnalysisTaskStatus> expectedStatuses,
            @Param("newStatus") AnalysisTaskStatus newStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Query("""
    SELECT new com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskRecoveryCandidate(
        task.id,
        task.status
    )
    FROM AnalysisTask task
    WHERE task.status IN :statuses
    ORDER BY task.updatedAt
    """)
    List<AnalysisTaskRecoveryCandidate> findRecoveryCandidates(
            @Param("statuses") Collection<AnalysisTaskStatus> statuses
    );

}
