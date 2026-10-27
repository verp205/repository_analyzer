package com.ivan_naumenko.repository_analyzer.commit.repository;

import com.ivan_naumenko.repository_analyzer.analysis.model.AnalysisTaskStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingStatus;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingData;
import com.ivan_naumenko.repository_analyzer.commit.model.CommitStatusCount;
import com.ivan_naumenko.repository_analyzer.commit.model.RepositoryCommit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RepositoryCommitRepository extends JpaRepository<RepositoryCommit, Integer> {

    @Query("SELECT c FROM RepositoryCommit c WHERE c.analysisTask.id = :taskId AND c.reachable = true")
    List<RepositoryCommit> findAllByAnalysisTask_Id(@Param("taskId") Integer analysisTaskId);

    @Query("SELECT c FROM RepositoryCommit c WHERE c.analysisTask.id = :taskId")
    List<RepositoryCommit> findStoredByTask(@Param("taskId") Integer analysisTaskId);

    @Query("""
            SELECT new com.ivan_naumenko.repository_analyzer.commit.model.CommitStatusCount(
                commit.processingStatus,
                COUNT(commit)
            )
            FROM RepositoryCommit commit
            WHERE commit.analysisTask.id = :taskId
              AND commit.reachable = true
            GROUP BY commit.processingStatus
            """)
    List<CommitStatusCount> countByStatus(
            @Param("taskId") Integer taskId
    );

    @Query("""
            SELECT commit.id
            FROM RepositoryCommit commit
            WHERE commit.analysisTask.id = :taskId
              AND commit.reachable = true
              AND commit.processingStatus = :status
            ORDER BY commit.committedAt ASC, commit.id ASC
            """)
    List<Integer> findIdsForReport(
            @Param("taskId") Integer taskId,
            @Param("status") CommitProcessingStatus status
    );

    @Query("SELECT COUNT(c) FROM RepositoryCommit c WHERE c.analysisTask.id = :analysisTaskId AND c.reachable = true AND c.processingStatus IN :statuses")
    long countByAnalysisTask_IdAndProcessingStatusIn(
            Integer analysisTaskId,
            Collection<CommitProcessingStatus> statuses
    );

    @Query("SELECT COUNT(c) > 0 FROM RepositoryCommit c WHERE c.analysisTask.id = :analysisTaskId AND c.reachable = true AND c.processingStatus = :status")
    boolean existsByAnalysisTask_IdAndProcessingStatus(
            Integer analysisTaskId,
            CommitProcessingStatus status
    );

    @Query("""
            SELECT new com.ivan_naumenko.repository_analyzer.commit.model.CommitProcessingData(
                commit.id,
                commit.analysisTask.id,
                commit.commitHash,
                commit.analysisTask.repositoryUrl,
                commit.analysisTask.branch,
                commit.authorName,
                commit.committedAt,
                commit.message
            )
            FROM RepositoryCommit commit
            WHERE commit.id = :commitId
              AND commit.processingStatus = :status
            """)
    Optional<CommitProcessingData> findProcessingData(
            @Param("commitId") Integer commitId,
            @Param("status") CommitProcessingStatus status
    );

    @Query(value = """
                SELECT commit_row.id
                FROM repository_commit commit_row
                JOIN analysis_task task
                  ON task.id = commit_row.analysis_task_id
                WHERE commit_row.analysis_task_id = :taskId
                  AND commit_row.processing_status = 'NEW'
                  AND commit_row.reachable = 1
                  AND task.status = 'PROCESSING'
                ORDER BY RANDOM()
                LIMIT 1
                """,
            nativeQuery = true
    )
    Optional<Integer> findRandomNewCommitId(
            @Param("taskId") Integer taskId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE RepositoryCommit r
            SET r.processingStatus = :newStatus,
                r.attemptCount = r.attemptCount + 1,
                r.updatedAt = :updatedAt
            WHERE r.id = :commitId
              AND r.processingStatus = :expectedStatus
            """)
    int updateProcessingStatusIfCurrent(
            @Param("commitId") Integer commitId,
            @Param("expectedStatus") CommitProcessingStatus expectedStatus,
            @Param("newStatus") CommitProcessingStatus newStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE RepositoryCommit r
            SET r.processingStatus = :newStatus,
                r.lastError = :lastError,
                r.updatedAt = :updatedAt
            WHERE r.id = :commitId
              AND r.processingStatus = :expectedStatus
            """)
    int finishProcessingIfCurrent(
            @Param("commitId") Integer commitId,
            @Param("expectedStatus") CommitProcessingStatus expectedStatus,
            @Param("newStatus") CommitProcessingStatus newStatus,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE RepositoryCommit r
        SET r.processingStatus = :newStatus,
            r.updatedAt = :updatedAt
        WHERE r.analysisTask.id = :taskId
          AND r.processingStatus = :expectedStatus
        """)
    int requeueInterruptedCommits(
            @Param("taskId") Integer taskId,
            @Param("expectedStatus") CommitProcessingStatus expectedStatus,
            @Param("newStatus") CommitProcessingStatus newStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE RepositoryCommit r
        SET r.processingStatus = :newStatus,
            r.updatedAt = :updatedAt
        WHERE r.analysisTask.id = :taskId
          AND r.processingStatus = :expectedStatus
          AND r.attemptCount < :maxAttempts
          AND r.analysisTask.status = :taskStatus
        """)
    int requeueRetryableFailedCommits(
            @Param("taskId") Integer taskId,
            @Param("expectedStatus") CommitProcessingStatus expectedStatus,
            @Param("newStatus") CommitProcessingStatus newStatus,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("maxAttempts") int maxAttempts,
            @Param("taskStatus") AnalysisTaskStatus taskStatus
    );

}
