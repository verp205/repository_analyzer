package com.ivan_naumenko.repository_analyzer.analysis.repository;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTask;
import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AnalysisTaskRepository extends JpaRepository<AnalysisTask, Integer> {

    Optional<AnalysisTask> findByRepositoryUrlAndBranch(
            String repositoryUrl,
            String branch
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
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

}
